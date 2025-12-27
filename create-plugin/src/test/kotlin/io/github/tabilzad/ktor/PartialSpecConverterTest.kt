package io.github.tabilzad.ktor

import io.github.tabilzad.ktor.model.*
import io.github.tabilzad.ktor.output.OpenApiSpec
import io.github.tabilzad.ktor.output.PartialSpecConverter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PartialSpecConverterTest {

    @Test
    @Suppress("LongMethod")
    fun `should convert OpenApiSpec to PartialOpenApiSpec preserving all fields`() {
        val openApiSpec = OpenApiSpec(
            openapi = "3.1.0",
            info = null,
            servers = null,
            paths = mapOf(
                "/users" to mapOf(
                    "get" to OpenApiSpec.Path(
                        summary = "Get all users",
                        description = "Returns a list of users",
                        operationId = "getUsers",
                        tags = listOf("users"),
                        deprecated = false,
                        responses = mapOf(
                            "200" to OpenApiSpec.ResponseDetails(
                                description = "Successful response",
                                content = mapOf(
                                    ContentType.APPLICATION_JSON to mapOf(
                                        "schema" to OpenApiSpec.TypeDescriptor(
                                            type = null,
                                            ref = "#/components/schemas/User"
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            ),
            components = OpenApiSpec.OpenApiComponents(
                schemas = mapOf(
                    "User" to OpenApiSpec.TypeDescriptor(
                        type = "object",
                        fqName = "com.example.User",
                        properties = mutableMapOf(
                            "id" to OpenApiSpec.TypeDescriptor(type = "integer"),
                            "name" to OpenApiSpec.TypeDescriptor(type = "string")
                        ),
                        required = mutableListOf("id", "name")
                    )
                ),
                securitySchemes = mapOf(
                    "bearerAuth" to SecurityScheme(
                        type = "http",
                        scheme = "bearer",
                        bearerFormat = "JWT"
                    )
                )
            ),
            security = null
        )

        val moduleId = ":feature-users"
        val partialSpec = PartialSpecConverter.toPartialSpec(openApiSpec, moduleId)

        assertThat(partialSpec.moduleId).isEqualTo(moduleId)
        assertThat(partialSpec.openapi).isEqualTo("3.1.0")
        assertThat(partialSpec.paths).hasSize(1)
        assertThat(partialSpec.paths["/users"]).hasSize(1)
        assertThat(partialSpec.paths["/users"]?.get("get")?.summary).isEqualTo("Get all users")
        assertThat(partialSpec.paths["/users"]?.get("get")?.operationId).isEqualTo("getUsers")
        assertThat(partialSpec.schemas).hasSize(1)
        assertThat(partialSpec.schemas["User"]?.type).isEqualTo("object")
        assertThat(partialSpec.securitySchemes).hasSize(1)
        assertThat(partialSpec.securitySchemes["bearerAuth"]?.type).isEqualTo("http")
    }

    @Test
    fun `should convert PartialOpenApiSpec back to OpenApiSpec`() {
        val partialSpec = PartialOpenApiSpec(
            moduleId = ":feature-products",
            openapi = "3.1.0",
            paths = mapOf(
                "/products" to mapOf(
                    "get" to PartialPath(
                        summary = "Get all products",
                        description = "Returns a list of products",
                        tags = listOf("products")
                    ),
                    "post" to PartialPath(
                        summary = "Create a product",
                        requestBody = PartialRequestBody(
                            required = true,
                            content = mapOf(
                                "application/json" to mapOf(
                                    "schema" to PartialTypeDescriptor(
                                        ref = "#/components/schemas/Product"
                                    )
                                )
                            )
                        )
                    )
                )
            ),
            schemas = mapOf(
                "Product" to PartialTypeDescriptor(
                    type = "object",
                    properties = mapOf(
                        "id" to PartialTypeDescriptor(type = "integer"),
                        "name" to PartialTypeDescriptor(type = "string"),
                        "price" to PartialTypeDescriptor(type = "number", format = "double")
                    ),
                    required = listOf("id", "name", "price")
                )
            )
        )

        val openApiSpec = PartialSpecConverter.fromPartialSpec(partialSpec)

        assertThat(openApiSpec.openapi).isEqualTo("3.1.0")
        assertThat(openApiSpec.paths).hasSize(1)
        assertThat(openApiSpec.paths["/products"]).hasSize(2)
        assertThat(openApiSpec.paths["/products"]?.get("get")?.summary).isEqualTo("Get all products")
        assertThat(openApiSpec.paths["/products"]?.get("post")?.requestBody).isNotNull
        assertThat(openApiSpec.components.schemas).hasSize(1)
        assertThat(openApiSpec.components.schemas["Product"]?.type).isEqualTo("object")
    }

    @Test
    fun `should merge multiple partial specs`() {
        val partialSpec1 = PartialOpenApiSpec(
            moduleId = ":feature-users",
            paths = mapOf(
                "/users" to mapOf(
                    "get" to PartialPath(summary = "Get users")
                )
            ),
            schemas = mapOf(
                "User" to PartialTypeDescriptor(type = "object")
            )
        )

        val partialSpec2 = PartialOpenApiSpec(
            moduleId = ":feature-products",
            paths = mapOf(
                "/products" to mapOf(
                    "get" to PartialPath(summary = "Get products")
                )
            ),
            schemas = mapOf(
                "Product" to PartialTypeDescriptor(type = "object")
            )
        )

        val mergedSpec = PartialSpecConverter.mergePartialSpecs(
            partialSpecs = listOf(partialSpec1, partialSpec2),
            localSpec = null
        )

        assertThat(mergedSpec.paths).hasSize(2)
        assertThat(mergedSpec.paths["/users"]).isNotNull
        assertThat(mergedSpec.paths["/products"]).isNotNull
        assertThat(mergedSpec.components.schemas).hasSize(2)
        assertThat(mergedSpec.components.schemas["User"]).isNotNull
        assertThat(mergedSpec.components.schemas["Product"]).isNotNull
    }

    @Test
    fun `should merge partial specs with local spec`() {
        val partialSpec = PartialOpenApiSpec(
            moduleId = ":feature-users",
            paths = mapOf(
                "/users" to mapOf(
                    "get" to PartialPath(summary = "Get users from feature module")
                )
            ),
            schemas = mapOf(
                "User" to PartialTypeDescriptor(type = "object")
            )
        )

        val localSpec = OpenApiSpec(
            openapi = "3.1.0",
            info = Info(
                title = "My API",
                description = "Main API",
                version = "1.0.0"
            ),
            servers = listOf(OpenApiSpec.Server("http://localhost:8080")),
            paths = mapOf(
                "/health" to mapOf(
                    "get" to OpenApiSpec.Path(summary = "Health check")
                )
            ),
            components = OpenApiSpec.OpenApiComponents(
                schemas = mapOf(
                    "HealthStatus" to OpenApiSpec.TypeDescriptor(type = "object")
                )
            ),
            security = listOf(mapOf("bearerAuth" to emptyList()))
        )

        val mergedSpec = PartialSpecConverter.mergePartialSpecs(
            partialSpecs = listOf(partialSpec),
            localSpec = localSpec
        )

        // Should have paths from both sources
        assertThat(mergedSpec.paths).hasSize(2)
        assertThat(mergedSpec.paths["/users"]).isNotNull
        assertThat(mergedSpec.paths["/health"]).isNotNull

        // Should have schemas from both sources
        assertThat(mergedSpec.components.schemas).hasSize(2)
        assertThat(mergedSpec.components.schemas["User"]).isNotNull
        assertThat(mergedSpec.components.schemas["HealthStatus"]).isNotNull

        // Should preserve local spec metadata
        assertThat(mergedSpec.info?.title).isEqualTo("My API")
        assertThat(mergedSpec.servers).hasSize(1)
        assertThat(mergedSpec.security).hasSize(1)
    }

    @Test
    fun `should handle local spec overriding partial spec paths`() {
        val partialSpec = PartialOpenApiSpec(
            moduleId = ":feature-module",
            paths = mapOf(
                "/shared" to mapOf(
                    "get" to PartialPath(summary = "From feature module")
                )
            )
        )

        val localSpec = OpenApiSpec(
            openapi = "3.1.0",
            info = null,
            paths = mapOf(
                "/shared" to mapOf(
                    "get" to OpenApiSpec.Path(summary = "From main module")
                )
            ),
            components = OpenApiSpec.OpenApiComponents(schemas = emptyMap())
        )

        val mergedSpec = PartialSpecConverter.mergePartialSpecs(
            partialSpecs = listOf(partialSpec),
            localSpec = localSpec
        )

        // Local spec should override partial spec
        assertThat(mergedSpec.paths["/shared"]?.get("get")?.summary).isEqualTo("From main module")
    }

    @Test
    fun `should handle request body with different content types`() {
        val openApiSpec = OpenApiSpec(
            openapi = "3.1.0",
            info = null,
            paths = mapOf(
                "/data" to mapOf(
                    "post" to OpenApiSpec.Path(
                        summary = "Post data",
                        requestBody = OpenApiSpec.RequestBody(
                            required = true,
                            content = mapOf(
                                ContentType.APPLICATION_JSON to mapOf(
                                    "schema" to OpenApiSpec.TypeDescriptor(type = "object")
                                ),
                                ContentType.TEXT_PLAIN to mapOf(
                                    "schema" to OpenApiSpec.TypeDescriptor(type = "string")
                                )
                            )
                        )
                    )
                )
            ),
            components = OpenApiSpec.OpenApiComponents(schemas = emptyMap())
        )

        val partialSpec = PartialSpecConverter.toPartialSpec(openApiSpec, ":module")
        val requestContent = partialSpec.paths["/data"]?.get("post")?.requestBody?.content

        assertThat(requestContent).hasSize(2)
        assertThat(requestContent?.get("application/json")).isNotNull
        assertThat(requestContent?.get("text/plain")).isNotNull

        // Convert back and verify
        val convertedBack = PartialSpecConverter.fromPartialSpec(partialSpec)
        val backContent = convertedBack.paths["/data"]?.get("post")?.requestBody?.content

        assertThat(backContent).hasSize(2)
        assertThat(backContent?.get(ContentType.APPLICATION_JSON)).isNotNull
        assertThat(backContent?.get(ContentType.TEXT_PLAIN)).isNotNull
    }

    @Test
    fun `should preserve parameters during conversion`() {
        val openApiSpec = OpenApiSpec(
            openapi = "3.1.0",
            info = null,
            paths = mapOf(
                "/users/{id}" to mapOf(
                    "get" to OpenApiSpec.Path(
                        summary = "Get user by ID",
                        parameters = listOf(
                            OpenApiSpec.Parameter(
                                name = "id",
                                `in` = "path",
                                required = true,
                                description = "User ID",
                                schema = OpenApiSpec.TypeDescriptor(type = "string")
                            ),
                            OpenApiSpec.Parameter(
                                name = "include",
                                `in` = "query",
                                required = false,
                                description = "Fields to include",
                                schema = OpenApiSpec.TypeDescriptor(type = "string")
                            )
                        )
                    )
                )
            ),
            components = OpenApiSpec.OpenApiComponents(schemas = emptyMap())
        )

        val partialSpec = PartialSpecConverter.toPartialSpec(openApiSpec, ":module")
        val params = partialSpec.paths["/users/{id}"]?.get("get")?.parameters

        assertThat(params).hasSize(2)
        assertThat(params?.get(0)?.name).isEqualTo("id")
        assertThat(params?.get(0)?.`in`).isEqualTo("path")
        assertThat(params?.get(0)?.required).isTrue
        assertThat(params?.get(1)?.name).isEqualTo("include")
        assertThat(params?.get(1)?.`in`).isEqualTo("query")
        assertThat(params?.get(1)?.required).isFalse
    }

    @Test
    fun `should handle discriminator in type descriptors`() {
        val openApiSpec = OpenApiSpec(
            openapi = "3.1.0",
            info = null,
            paths = emptyMap(),
            components = OpenApiSpec.OpenApiComponents(
                schemas = mapOf(
                    "Pet" to OpenApiSpec.TypeDescriptor(
                        type = "object",
                        discriminator = OpenApiSpec.DiscriminatorDescriptor(
                            propertyName = "petType",
                            mapping = mapOf(
                                "dog" to "#/components/schemas/Dog",
                                "cat" to "#/components/schemas/Cat"
                            )
                        ),
                        oneOf = listOf(
                            OpenApiSpec.TypeDescriptor(type = null, ref = "#/components/schemas/Dog"),
                            OpenApiSpec.TypeDescriptor(type = null, ref = "#/components/schemas/Cat")
                        )
                    )
                )
            )
        )

        val partialSpec = PartialSpecConverter.toPartialSpec(openApiSpec, ":module")
        val petSchema = partialSpec.schemas["Pet"]

        assertThat(petSchema?.discriminator).isNotNull
        assertThat(petSchema?.discriminator?.propertyName).isEqualTo("petType")
        assertThat(petSchema?.discriminator?.mapping).hasSize(2)
        assertThat(petSchema?.oneOf).hasSize(2)

        // Convert back and verify
        val convertedBack = PartialSpecConverter.fromPartialSpec(partialSpec)
        val backPetSchema = convertedBack.components.schemas["Pet"]

        assertThat(backPetSchema?.discriminator?.propertyName).isEqualTo("petType")
        assertThat(backPetSchema?.discriminator?.mapping).hasSize(2)
    }

    @Test
    fun `should handle empty partial specs list`() {
        val mergedSpec = PartialSpecConverter.mergePartialSpecs(
            partialSpecs = emptyList(),
            localSpec = null
        )

        assertThat(mergedSpec.paths).isEmpty()
        assertThat(mergedSpec.components.schemas).isEmpty()
    }

    @Test
    fun `should merge security schemes from multiple modules`() {
        val partialSpec1 = PartialOpenApiSpec(
            moduleId = ":auth-module",
            securitySchemes = mapOf(
                "bearerAuth" to SecurityScheme("http", scheme = "bearer")
            )
        )

        val partialSpec2 = PartialOpenApiSpec(
            moduleId = ":api-key-module",
            securitySchemes = mapOf(
                "apiKeyAuth" to SecurityScheme("apiKey", name = "X-API-Key", `in` = "header")
            )
        )

        val mergedSpec = PartialSpecConverter.mergePartialSpecs(
            partialSpecs = listOf(partialSpec1, partialSpec2),
            localSpec = null
        )

        assertThat(mergedSpec.components.securitySchemes).hasSize(2)
        assertThat(mergedSpec.components.securitySchemes?.get("bearerAuth")).isNotNull
        assertThat(mergedSpec.components.securitySchemes?.get("apiKeyAuth")).isNotNull
    }
}
