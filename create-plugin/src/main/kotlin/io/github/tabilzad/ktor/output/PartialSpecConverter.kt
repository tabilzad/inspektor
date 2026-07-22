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
    val spec: OpenApiSpec
) {
    companion object {
        /** Bump when the IR format changes incompatibly. */
        const val CURRENT_VERSION = 1
    }
}

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

    fun encode(spec: OpenApiSpec, moduleId: String): String =
        json.encodeToString(PartialOpenApiSpec.serializer(), PartialOpenApiSpec(moduleId = moduleId, spec = spec))

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
