package io.github.tabilzad.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.tabilzad.ktor.model.PartialOpenApiSpec
import io.github.tabilzad.ktor.model.PartialSpecLocation
import io.github.tabilzad.ktor.output.OpenApiSpec
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Integration tests for multi-module OpenAPI spec generation.
 *
 * These tests verify the end-to-end flow of:
 * 1. Contributor modules generating partial specs
 * 2. Aggregator modules merging partial specs from dependencies
 * 3. Correct handling of routes, schemas, and security schemes across modules
 */
@OptIn(ExperimentalCompilerApi::class, ExperimentalEncodingApi::class)
class MultiModuleIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    private val objectMapper = jacksonObjectMapper()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private lateinit var contributorResourcesDir: File
    private lateinit var aggregatorOutputFile: File

    @BeforeEach
    fun setup() {
        contributorResourcesDir = tempDir.resolve("contributor-resources").toFile().apply { mkdirs() }
        aggregatorOutputFile = tempDir.resolve("openapi.json").toFile()
    }

    @Test
    fun `contributor module should generate partial spec in resources`() {
        val contributorSource = TestUtils.loadMultiModuleSource("UsersContributor")

        compileContributorModule(
            moduleId = ":feature-users",
            source = contributorSource,
            resourcesDir = contributorResourcesDir
        )

        val partialSpecFile = File(contributorResourcesDir, PartialSpecLocation.FULL_PATH)
        assertThat(partialSpecFile).exists()

        val partialSpec = json.decodeFromString(PartialOpenApiSpec.serializer(), partialSpecFile.readText())

        assertThat(partialSpec.moduleId).isEqualTo(":feature-users")
        assertThat(partialSpec.paths).hasSize(2) // /users and /users/{id}
        assertThat(partialSpec.paths["/users"]).containsKeys("get", "post")
        assertThat(partialSpec.paths["/users/{id}"]).containsKey("get")
        // Plugin extracts schemas from receive<> calls (request bodies)
        assertThat(partialSpec.schemas).containsKey("com.example.users.CreateUserRequest")
    }

    @Test
    fun `aggregator module should merge partial specs from contributor`() {
        val contributorSource = TestUtils.loadMultiModuleSource("ProductsContributor")

        compileContributorModule(
            moduleId = ":feature-products",
            source = contributorSource,
            resourcesDir = contributorResourcesDir
        )

        val aggregatorSource = TestUtils.loadMultiModuleSource("HealthAggregator")

        compileAggregatorModule(
            moduleId = ":server",
            source = aggregatorSource,
            outputFile = aggregatorOutputFile,
            contributorResourcesDirs = listOf(contributorResourcesDir)
        )

        assertThat(aggregatorOutputFile).exists()
        val mergedSpec = objectMapper.readValue<OpenApiSpec>(aggregatorOutputFile.readText())

        // Should have paths from both modules
        assertThat(mergedSpec.paths).containsKeys("/health", "/products", "/products/{id}")

        // Verify the contributor's routes are present
        assertThat(mergedSpec.paths["/products"]?.get("get")?.summary).isEqualTo("Get all products")
        assertThat(mergedSpec.paths["/products/{id}"]?.get("get")?.summary).isEqualTo("Get product by ID")

        // Verify the aggregator's local routes are present
        assertThat(mergedSpec.paths["/health"]?.get("get")?.summary).isEqualTo("Health check endpoint")
    }

    @Test
    fun `should merge multiple contributor modules`() {
        val usersResourcesDir = tempDir.resolve("users-resources").toFile().apply { mkdirs() }
        val ordersResourcesDir = tempDir.resolve("orders-resources").toFile().apply { mkdirs() }
        val paymentsResourcesDir = tempDir.resolve("payments-resources").toFile().apply { mkdirs() }

        // Compile all contributors
        compileContributorModule(
            moduleId = ":feature-users",
            source = TestUtils.loadMultiModuleSource("UsersContributor"),
            resourcesDir = usersResourcesDir
        )

        compileContributorModule(
            moduleId = ":feature-orders",
            source = TestUtils.loadMultiModuleSource("OrdersContributor"),
            resourcesDir = ordersResourcesDir
        )

        compileContributorModule(
            moduleId = ":feature-payments",
            source = TestUtils.loadMultiModuleSource("PaymentsContributor"),
            resourcesDir = paymentsResourcesDir
        )

        // Compile aggregator with all contributors
        compileAggregatorModule(
            moduleId = ":server",
            source = TestUtils.loadMultiModuleSource("EmptyAggregator"),
            outputFile = aggregatorOutputFile,
            contributorResourcesDirs = listOf(usersResourcesDir, ordersResourcesDir, paymentsResourcesDir)
        )

        val mergedSpec = objectMapper.readValue<OpenApiSpec>(aggregatorOutputFile.readText())

        // All paths should be present
        assertThat(mergedSpec.paths).containsKeys(
            "/users", "/users/{id}",
            "/orders",
            "/payments"
        )

        // Plugin extracts schemas from receive<> calls only - only ProcessPaymentRequest is received
        assertThat(mergedSpec.components.schemas).containsKey("com.example.payments.ProcessPaymentRequest")
    }

    @Test
    fun `should preserve request bodies and parameters across modules`() {
        compileContributorModule(
            moduleId = ":feature-items",
            source = TestUtils.loadMultiModuleSource("ItemsContributor"),
            resourcesDir = contributorResourcesDir
        )

        compileAggregatorModule(
            moduleId = ":server",
            source = TestUtils.loadMultiModuleSource("EmptyAggregator"),
            outputFile = aggregatorOutputFile,
            contributorResourcesDirs = listOf(contributorResourcesDir)
        )

        val mergedSpec = objectMapper.readValue<OpenApiSpec>(aggregatorOutputFile.readText())

        // Verify routes are present
        assertThat(mergedSpec.paths).containsKeys("/items", "/items/{id}")

        // Verify POST has request body
        val createPath = mergedSpec.paths["/items"]?.get("post")
        assertThat(createPath?.requestBody).isNotNull

        // Verify PUT has both request body and parameters
        val updatePath = mergedSpec.paths["/items/{id}"]?.get("put")
        assertThat(updatePath?.requestBody).isNotNull
        assertThat(updatePath?.parameters).isNotEmpty

        // Verify schemas are complete (only request body schemas are captured)
        assertThat(mergedSpec.components.schemas).containsKey("com.example.api.CreateItemRequest")
        val createRequestSchema = mergedSpec.components.schemas["com.example.api.CreateItemRequest"]
        assertThat(createRequestSchema?.properties).containsKeys("name", "description", "priceInCents")
    }

    @Test
    fun `should handle nested routes from contributor modules`() {
        compileContributorModule(
            moduleId = ":feature-admin",
            source = TestUtils.loadMultiModuleSource("NestedRoutesContributor"),
            resourcesDir = contributorResourcesDir
        )

        compileAggregatorModule(
            moduleId = ":server",
            source = TestUtils.loadMultiModuleSource("EmptyAggregator"),
            outputFile = aggregatorOutputFile,
            contributorResourcesDirs = listOf(contributorResourcesDir)
        )

        val mergedSpec = objectMapper.readValue<OpenApiSpec>(aggregatorOutputFile.readText())

        // Verify deeply nested routes are correctly merged
        assertThat(mergedSpec.paths).containsKeys(
            "/api/v1/admin/users",
            "/api/v1/admin/users/{id}",
            "/api/v1/admin/audit/logs"
        )

        // Verify route metadata is preserved
        assertThat(mergedSpec.paths["/api/v1/admin/users"]?.get("get")?.summary).isEqualTo("List admin users")
        assertThat(mergedSpec.paths["/api/v1/admin/audit/logs"]?.get("get")?.summary).isEqualTo("Get audit logs")
    }

    @Test
    fun `aggregator local routes should override contributor routes with same path`() {
        compileContributorModule(
            moduleId = ":feature-shared",
            source = TestUtils.loadMultiModuleSource("SharedPathContributor"),
            resourcesDir = contributorResourcesDir
        )

        compileAggregatorModule(
            moduleId = ":server",
            source = TestUtils.loadMultiModuleSource("SharedPathAggregator"),
            outputFile = aggregatorOutputFile,
            contributorResourcesDirs = listOf(contributorResourcesDir)
        )

        val mergedSpec = objectMapper.readValue<OpenApiSpec>(aggregatorOutputFile.readText())

        // The aggregator's version should win
        assertThat(mergedSpec.paths["/shared"]?.get("get")?.summary)
            .isEqualTo("Shared endpoint from aggregator (override)")
    }

    @Test
    fun `contributor module with deprecated routes should preserve deprecation flag`() {
        compileContributorModule(
            moduleId = ":feature-legacy",
            source = TestUtils.loadMultiModuleSource("DeprecatedContributor"),
            resourcesDir = contributorResourcesDir
        )

        compileAggregatorModule(
            moduleId = ":server",
            source = TestUtils.loadMultiModuleSource("EmptyAggregator"),
            outputFile = aggregatorOutputFile,
            contributorResourcesDirs = listOf(contributorResourcesDir)
        )

        val mergedSpec = objectMapper.readValue<OpenApiSpec>(aggregatorOutputFile.readText())

        // V1 should be deprecated
        assertThat(mergedSpec.paths["/api/v1/items"]?.get("get")?.deprecated).isTrue

        // V2 should not be deprecated
        assertThat(mergedSpec.paths["/api/v2/items"]?.get("get")?.deprecated).isNull()
    }

    // ==================== Helper Functions ====================

    private fun compileContributorModule(
        moduleId: String,
        source: String,
        resourcesDir: File
    ) {
        val clp = KtorDocsCommandLineProcessor()
        val compilation = KotlinCompilation().apply {
            compilerPluginRegistrars = listOf(KtorMetaPluginRegistrar())
            commandLineProcessors = listOf(clp)
            classpaths = testDependencies.map { classpathOf(it) }
            sources = listOf(
                SourceFile.kotlin("RequestDataClasses.kt", loadRequestDataClasses()),
                SourceFile.kotlin("ContributorModule.kt", source)
            )
            jvmTarget = "11"
            messageOutputStream = createFilteredOutputStream()
            pluginOptions = createPluginOptions(clp) + listOf(
                com.tschuchort.compiletesting.PluginOption(
                    clp.pluginId,
                    KtorDocsCommandLineProcessor.moduleIdOption.optionName,
                    moduleId
                ),
                com.tschuchort.compiletesting.PluginOption(
                    clp.pluginId,
                    KtorDocsCommandLineProcessor.isAggregatorOption.optionName,
                    "false"
                ),
                com.tschuchort.compiletesting.PluginOption(
                    clp.pluginId,
                    KtorDocsCommandLineProcessor.resourcesPathOption.optionName,
                    resourcesDir.absolutePath
                )
            )
        }

        val result = compilation.compile()
        assertThat(result.exitCode)
            .withFailMessage { "Contributor compilation failed:\n${result.messages}" }
            .isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    private fun compileAggregatorModule(
        moduleId: String,
        source: String,
        outputFile: File,
        contributorResourcesDirs: List<File>
    ) {
        val clp = KtorDocsCommandLineProcessor()

        // Build paths to partial spec files from contributor modules
        val partialSpecPaths = contributorResourcesDirs.map { dir ->
            File(dir, PartialSpecLocation.FULL_PATH).absolutePath
        }

        val compilation = KotlinCompilation().apply {
            compilerPluginRegistrars = listOf(KtorMetaPluginRegistrar())
            commandLineProcessors = listOf(clp)
            classpaths = testDependencies.map { classpathOf(it) }
            sources = listOf(
                SourceFile.kotlin("RequestDataClasses.kt", loadRequestDataClasses()),
                SourceFile.kotlin("AggregatorModule.kt", source)
            )
            jvmTarget = "11"
            messageOutputStream = createFilteredOutputStream()
            pluginOptions = createPluginOptions(clp, outputFile.absolutePath) + listOf(
                com.tschuchort.compiletesting.PluginOption(
                    clp.pluginId,
                    KtorDocsCommandLineProcessor.moduleIdOption.optionName,
                    moduleId
                ),
                com.tschuchort.compiletesting.PluginOption(
                    clp.pluginId,
                    KtorDocsCommandLineProcessor.isAggregatorOption.optionName,
                    "true"
                ),
                com.tschuchort.compiletesting.PluginOption(
                    clp.pluginId,
                    KtorDocsCommandLineProcessor.partialSpecPathsOption.optionName,
                    partialSpecPaths.joinToString("||")
                )
            )
        }

        val result = compilation.compile()
        assertThat(result.exitCode)
            .withFailMessage { "Aggregator compilation failed:\n${result.messages}" }
            .isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    private fun createPluginOptions(
        clp: KtorDocsCommandLineProcessor,
        outputPath: String = tempDir.resolve("unused.json").toString()
    ): List<com.tschuchort.compiletesting.PluginOption> {
        return listOf(
            com.tschuchort.compiletesting.PluginOption(
                clp.pluginId,
                KtorDocsCommandLineProcessor.isEnabled.optionName,
                "true"
            ),
            com.tschuchort.compiletesting.PluginOption(
                clp.pluginId,
                KtorDocsCommandLineProcessor.requestSchema.optionName,
                "true"
            ),
            com.tschuchort.compiletesting.PluginOption(
                clp.pluginId,
                KtorDocsCommandLineProcessor.formatOption.optionName,
                "json"
            ),
            com.tschuchort.compiletesting.PluginOption(
                clp.pluginId,
                KtorDocsCommandLineProcessor.pathOption.optionName,
                outputPath
            ),
            com.tschuchort.compiletesting.PluginOption(
                clp.pluginId,
                KtorDocsCommandLineProcessor.hideTransientFields.optionName,
                "true"
            ),
            com.tschuchort.compiletesting.PluginOption(
                clp.pluginId,
                KtorDocsCommandLineProcessor.hidePrivateAndInternalFields.optionName,
                "true"
            ),
            com.tschuchort.compiletesting.PluginOption(
                clp.pluginId,
                KtorDocsCommandLineProcessor.initConfig.optionName,
                Base64.encode(
                    Json.encodeToString(
                        io.github.tabilzad.ktor.model.ConfigInput()
                    ).toByteArray()
                )
            )
        )
    }

    private fun loadRequestDataClasses(): String {
        return TestUtils.loadRequests.readText()
    }
}
