package io.github.tabilzad.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.tabilzad.ktor.model.Info
import io.github.tabilzad.ktor.model.SecurityScheme
import io.github.tabilzad.ktor.output.OpenApiSpec
import io.github.tabilzad.ktor.output.PartialOpenApiSpec
import io.github.tabilzad.ktor.output.PartialSpecs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for the multi-module IR: envelope encode/decode round-trips and
 * the merge semantics between contributor partials and the aggregator's local spec.
 */
class PartialSpecsTest {

    // ==================== Round-trip ====================

    @Test
    fun `encode-decode round trip preserves paths, schemas and module identity`() {
        val spec = specWith(
            paths = mapOf("/users" to mapOf("get" to path(summary = "List users"))),
            schemas = mapOf("com.example.User" to typeDescriptor(fqName = "com.example.User"))
        )

        val decoded = PartialSpecs.decode(PartialSpecs.encode(spec, moduleId = ":feature-users"))

        assertThat(decoded.moduleId).isEqualTo(":feature-users")
        assertThat(decoded.version).isEqualTo(PartialOpenApiSpec.CURRENT_VERSION)
        assertThat(decoded.spec.paths["/users"]?.get("get")?.summary).isEqualTo("List users")
        assertThat(decoded.spec.components.schemas).containsKey("com.example.User")
    }

    @Test
    fun `round trip preserves schema fqName for cross-module identity`() {
        val spec = specWith(
            schemas = mapOf("User" to typeDescriptor(fqName = "com.example.User"))
        )

        val decoded = PartialSpecs.decode(PartialSpecs.encode(spec, ":m"))

        assertThat(decoded.spec.components.schemas["User"]?.fqName).isEqualTo("com.example.User")
    }

    @Test
    fun `fqName stays out of the final Jackson output despite being carried in the IR`() {
        val descriptor = typeDescriptor(fqName = "com.example.User")

        val finalOutput = jacksonObjectMapper().writeValueAsString(descriptor)

        assertThat(finalOutput).doesNotContain("fqName")
    }

    @Test
    fun `round trip preserves custom response content types`() {
        val spec = specWith(
            paths = mapOf(
                "/report" to mapOf(
                    "get" to path(
                        responses = mapOf(
                            "200" to OpenApiSpec.ResponseDetails(
                                description = "PDF report",
                                content = mapOf(
                                    "application/pdf" to mapOf(
                                        "schema" to typeDescriptor(type = "string", format = "binary")
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val decoded = PartialSpecs.decode(PartialSpecs.encode(spec, ":m"))

        val content = decoded.spec.paths["/report"]?.get("get")?.responses?.get("200")?.content
        assertThat(content).containsKey("application/pdf")
        assertThat(content?.get("application/pdf")?.get("schema")?.format).isEqualTo("binary")
    }

    @Test
    fun `encoded envelope always carries an explicit format version`() {
        val encoded = PartialSpecs.encode(specWith(), ":m")

        assertThat(encoded).contains("\"version\": ${PartialOpenApiSpec.CURRENT_VERSION}")
    }

    @Test
    fun `decoding tolerates unknown fields and newer format versions`() {
        // Simulates a partial produced by a future plugin version with extra fields.
        val encoded = """
            {
                "version": 99,
                "futureField": "ignored",
                "moduleId": ":m",
                "spec": { "paths": {}, "components": { "schemas": {} } }
            }
        """.trimIndent()

        val decoded = PartialSpecs.decode(encoded)

        assertThat(decoded.version).isEqualTo(99)
        assertThat(decoded.moduleId).isEqualTo(":m")
    }

    // ==================== Merge semantics ====================

    @Test
    fun `merge combines paths and schemas from multiple contributors`() {
        val users = partial(
            ":users",
            paths = mapOf("/users" to mapOf("get" to path(summary = "List users"))),
            schemas = mapOf("User" to typeDescriptor(fqName = "com.example.User"))
        )
        val orders = partial(
            ":orders",
            paths = mapOf("/orders" to mapOf("get" to path(summary = "List orders"))),
            schemas = mapOf("Order" to typeDescriptor(fqName = "com.example.Order"))
        )

        val merged = PartialSpecs.merge(listOf(users, orders), localSpec = null) { }

        assertThat(merged.paths).containsKeys("/users", "/orders")
        assertThat(merged.components.schemas).containsKeys("User", "Order")
    }

    @Test
    fun `top-level metadata comes from the aggregator only`() {
        val contributor = partial(":c", paths = mapOf("/c" to mapOf("get" to path())))
        val local = specWith(
            info = Info(title = "Server API", version = "2.0.0"),
            paths = mapOf("/local" to mapOf("get" to path()))
        )

        val merged = PartialSpecs.merge(listOf(contributor), local) { }

        assertThat(merged.info?.title).isEqualTo("Server API")
        assertThat(merged.paths).containsKeys("/c", "/local")
    }

    @Test
    fun `first contributor wins on conflicting duplicate and conflict is reported`() {
        val first = partial(":a", paths = mapOf("/dup" to mapOf("get" to path(summary = "From A"))))
        val second = partial(":b", paths = mapOf("/dup" to mapOf("get" to path(summary = "From B"))))
        val reports = mutableListOf<String>()

        val merged = PartialSpecs.merge(listOf(first, second), localSpec = null, reports::add)

        assertThat(merged.paths["/dup"]?.get("get")?.summary).isEqualTo("From A")
        assertThat(reports).hasSize(1)
        assertThat(reports.first()).contains("get /dup", ":a", ":b")
    }

    @Test
    fun `structurally identical duplicates are merged silently`() {
        val first = partial(":a", paths = mapOf("/same" to mapOf("get" to path(summary = "Same"))))
        val second = partial(":b", paths = mapOf("/same" to mapOf("get" to path(summary = "Same"))))
        val reports = mutableListOf<String>()

        PartialSpecs.merge(listOf(first, second), localSpec = null, reports::add)

        assertThat(reports).isEmpty()
    }

    @Test
    fun `schema conflicts between contributors are reported and first wins`() {
        val first = partial(
            ":a",
            schemas = mapOf("User" to typeDescriptor(fqName = "com.a.User", description = "A's user"))
        )
        val second = partial(
            ":b",
            schemas = mapOf("User" to typeDescriptor(fqName = "com.b.User", description = "B's user"))
        )
        val reports = mutableListOf<String>()

        val merged = PartialSpecs.merge(listOf(first, second), localSpec = null, reports::add)

        assertThat(merged.components.schemas["User"]?.description).isEqualTo("A's user")
        assertThat(reports).hasSize(1)
        assertThat(reports.first()).contains("schema 'User'")
    }

    @Test
    fun `local aggregator definitions override contributors and the override is reported`() {
        val contributor = partial(
            ":c",
            paths = mapOf("/shared" to mapOf("get" to path(summary = "From contributor")))
        )
        val local = specWith(paths = mapOf("/shared" to mapOf("get" to path(summary = "From aggregator"))))
        val reports = mutableListOf<String>()

        val merged = PartialSpecs.merge(listOf(contributor), local, reports::add)

        assertThat(merged.paths["/shared"]?.get("get")?.summary).isEqualTo("From aggregator")
        assertThat(reports).hasSize(1)
        assertThat(reports.first()).contains("Local definition", ":c")
    }

    @Test
    fun `security schemes are merged with contributor and local sources`() {
        val contributor = partial(
            ":c",
            securitySchemes = mapOf("apiKey" to SecurityScheme(type = "apiKey", `in` = "header", name = "X-Key"))
        )
        val local = specWith(
            securitySchemes = mapOf("bearer" to SecurityScheme(type = "http", scheme = "bearer"))
        )

        val merged = PartialSpecs.merge(listOf(contributor), local) { }

        assertThat(merged.components.securitySchemes).containsKeys("apiKey", "bearer")
    }

    // ==================== Helpers ====================

    private fun specWith(
        info: Info? = null,
        paths: Map<String, Map<String, OpenApiSpec.Path>> = emptyMap(),
        schemas: Map<String, OpenApiSpec.TypeDescriptor> = emptyMap(),
        securitySchemes: Map<String, SecurityScheme>? = null
    ) = OpenApiSpec(
        info = info,
        paths = paths,
        components = OpenApiSpec.OpenApiComponents(schemas = schemas, securitySchemes = securitySchemes)
    )

    private fun partial(
        moduleId: String,
        paths: Map<String, Map<String, OpenApiSpec.Path>> = emptyMap(),
        schemas: Map<String, OpenApiSpec.TypeDescriptor> = emptyMap(),
        securitySchemes: Map<String, SecurityScheme>? = null
    ) = PartialOpenApiSpec(
        moduleId = moduleId,
        spec = specWith(paths = paths, schemas = schemas, securitySchemes = securitySchemes)
    )

    private fun path(
        summary: String? = null,
        responses: Map<String, OpenApiSpec.ResponseDetails>? = null
    ) = OpenApiSpec.Path(summary = summary, responses = responses)

    private fun typeDescriptor(
        type: String? = "object",
        fqName: String? = null,
        description: String? = null,
        format: String? = null
    ) = OpenApiSpec.TypeDescriptor(
        type = type,
        fqName = fqName,
        description = description,
        format = format
    )
}
