package io.github.tabilzad.ktor.output

import io.github.tabilzad.ktor.model.SecurityScheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The intermediate representation a contributor module embeds at
 * `META-INF/inspektor/openapi-partial.json` for aggregation. The payload is the plugin's own
 * [OpenApiSpec] model serialized verbatim — there is deliberately no parallel mirror model to
 * keep in sync: any field added to [OpenApiSpec] is carried through multi-module aggregation
 * automatically.
 *
 * Because partials are baked into published JARs, the envelope carries a [version] so an
 * aggregator can recognize partials produced by a newer plugin than itself.
 */
@Serializable
data class PartialOpenApiSpec(
    val version: Int = CURRENT_VERSION,
    val moduleId: String,
    val spec: OpenApiSpec,
    /**
     * KDoc sidecar: class/property documentation for classes defined in the contributor,
     * keyed by fully qualified class name. Lets the aggregator enrich schemas it derived
     * from this module's compiled binaries, where KDocs no longer exist. Optional with a
     * default, so the envelope format stays compatible in both directions.
     */
    val schemaDocs: Map<String, SchemaDocs> = emptyMap()
) {
    companion object {
        /** Bump when the IR format changes incompatibly. */
        const val CURRENT_VERSION = 1
    }
}

/**
 * Class and property documentation extracted from a contributor's KDocs. Property docs are
 * keyed by the SERIALIZED property name (honoring @SerialName / moshi @Json) so they match
 * schema property keys directly at enrichment time.
 */
@Serializable
data class SchemaDocs(
    val classDoc: String? = null,
    val propertyDocs: Map<String, String> = emptyMap()
)

/**
 * Serialization and merge logic for multi-module partial specs.
 */
internal object PartialSpecs {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        // The IR is a persistent format baked into published JARs: always write defaulted
        // fields (most importantly the envelope version) so readers never have to guess.
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(
        spec: OpenApiSpec,
        moduleId: String,
        schemaDocs: Map<String, SchemaDocs> = emptyMap()
    ): String = json.encodeToString(
        PartialOpenApiSpec.serializer(),
        PartialOpenApiSpec(moduleId = moduleId, spec = spec, schemaDocs = schemaDocs)
    )

    fun decode(text: String): PartialOpenApiSpec =
        json.decodeFromString(PartialOpenApiSpec.serializer(), text)

    /**
     * Merges contributor partials with the aggregator's own [localSpec].
     *
     * Precedence:
     * - across contributors, the first definition of a path+method / schema / security scheme
     *   wins; a structurally different duplicate is reported through [report];
     * - the aggregator's local definitions always override contributor ones (reported when
     *   they actually differ).
     *
     * Top-level metadata (info, servers, global security) comes from the aggregator only.
     */
    fun merge(
        partials: List<PartialOpenApiSpec>,
        localSpec: OpenApiSpec?,
        report: (String) -> Unit
    ): OpenApiSpec {
        val merger = Merger(report)
        partials.forEach(merger::addContributor)
        localSpec?.let(merger::overlayLocal)

        return OpenApiSpec(
            info = localSpec?.info,
            servers = localSpec?.servers,
            paths = merger.paths.mapValues { it.value.toMap() },
            components = OpenApiSpec.OpenApiComponents(
                schemas = merger.schemas,
                securitySchemes = merger.securitySchemes.takeIf { it.isNotEmpty() }
            ),
            security = localSpec?.security
        )
    }

    /**
     * Fills in missing class/property descriptions on [spec]'s schemas from contributor KDoc
     * sidecars. Enrichment never overrides an existing description — inline KDocs, annotations
     * (@KtorSchema/@KtorField), and locally-derived docs always win — so it is safe to apply
     * unconditionally after merging. Inline descriptors (enum fields, array items, nested
     * objects) are matched through the fqName each descriptor carries in the IR.
     */
    fun enrichSchemaDescriptions(spec: OpenApiSpec, docs: Map<String, SchemaDocs>) {
        if (docs.isEmpty()) return
        val visited = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<OpenApiSpec.TypeDescriptor, Boolean>()
        )
        spec.components.schemas.forEach { (key, schema) ->
            enrichDescriptor(schema, docs[schema.fqName ?: key], docs, visited)
        }
    }

    private fun enrichDescriptor(
        descriptor: OpenApiSpec.TypeDescriptor,
        ownDocs: SchemaDocs?,
        allDocs: Map<String, SchemaDocs>,
        visited: MutableSet<OpenApiSpec.TypeDescriptor>
    ) {
        if (!visited.add(descriptor)) return

        val resolvedOwn = ownDocs ?: descriptor.fqName?.let(allDocs::get)
        if (descriptor.description == null) {
            descriptor.description = resolvedOwn?.classDoc
        }

        descriptor.properties?.forEach { (name, child) ->
            if (child.description == null) {
                child.description = resolvedOwn?.propertyDocs?.get(name)
                    ?: child.fqName?.let(allDocs::get)?.classDoc
            }
            enrichDescriptor(child, child.fqName?.let(allDocs::get), allDocs, visited)
        }
        descriptor.items?.let { enrichDescriptor(it, it.fqName?.let(allDocs::get), allDocs, visited) }
        descriptor.additionalProperties?.let { enrichDescriptor(it, it.fqName?.let(allDocs::get), allDocs, visited) }
        descriptor.oneOf?.forEach { enrichDescriptor(it, it.fqName?.let(allDocs::get), allDocs, visited) }
    }

    private class Merger(private val report: (String) -> Unit) {
        val paths = mutableMapOf<String, MutableMap<String, OpenApiSpec.Path>>()
        val schemas = mutableMapOf<String, OpenApiSpec.TypeDescriptor>()
        val securitySchemes = mutableMapOf<String, SecurityScheme>()

        private val pathOrigin = mutableMapOf<Pair<String, String>, String>()
        private val schemaOrigin = mutableMapOf<String, String>()

        fun addContributor(partial: PartialOpenApiSpec) {
            for ((path, methods) in partial.spec.paths) {
                val existing = paths.getOrPut(path) { mutableMapOf() }
                for ((method, operation) in methods) {
                    val previous = existing[method]
                    when {
                        previous == null -> {
                            existing[method] = operation
                            pathOrigin[path to method] = partial.moduleId
                        }

                        previous.structurallyDiffersFrom(operation) -> report(
                            "Conflicting definitions of '$method $path' from modules " +
                                "'${pathOrigin[path to method]}' and '${partial.moduleId}'; keeping the first."
                        )
                    }
                }
            }

            for ((name, schema) in partial.spec.components.schemas) {
                val previous = schemas[name]
                when {
                    previous == null -> {
                        schemas[name] = schema
                        schemaOrigin[name] = partial.moduleId
                    }

                    previous.structurallyDiffersFrom(schema) -> report(
                        "Conflicting definitions of schema '$name' from modules " +
                            "'${schemaOrigin[name]}' and '${partial.moduleId}'; keeping the first."
                    )
                }
            }

            partial.spec.components.securitySchemes?.forEach { (name, scheme) ->
                if (!securitySchemes.containsKey(name)) securitySchemes[name] = scheme
            }
        }

        fun overlayLocal(localSpec: OpenApiSpec) {
            for ((path, methods) in localSpec.paths) {
                val existing = paths.getOrPut(path) { mutableMapOf() }
                for ((method, operation) in methods) {
                    val previous = existing[method]
                    if (previous != null && previous.structurallyDiffersFrom(operation)) {
                        report(
                            "Local definition of '$method $path' overrides the one from module " +
                                "'${pathOrigin[path to method]}'."
                        )
                    }
                    existing[method] = operation
                }
            }
            schemas.putAll(localSpec.components.schemas)
            localSpec.components.securitySchemes?.let(securitySchemes::putAll)
        }

        // Data-class equality is not structural for TypeDescriptor (it compares fqName only),
        // so conflict detection compares the serialized forms instead.
        private fun OpenApiSpec.Path.structurallyDiffersFrom(other: OpenApiSpec.Path): Boolean =
            json.encodeToString(OpenApiSpec.Path.serializer(), this) !=
                json.encodeToString(OpenApiSpec.Path.serializer(), other)

        private fun OpenApiSpec.TypeDescriptor.structurallyDiffersFrom(
            other: OpenApiSpec.TypeDescriptor
        ): Boolean =
            json.encodeToString(OpenApiSpec.TypeDescriptor.serializer(), this) !=
                json.encodeToString(OpenApiSpec.TypeDescriptor.serializer(), other)
    }
}
