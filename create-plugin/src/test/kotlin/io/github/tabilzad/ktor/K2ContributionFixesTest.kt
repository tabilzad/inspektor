package io.github.tabilzad.ktor

import io.github.tabilzad.ktor.TestUtils.loadSourceCodeFrom
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Regression tests for a batch of correctness fixes:
 *  - the `useKDocs` config option was parsed but never read by the compiler (always defaulted true)
 *  - generated path/method ordering was non-deterministic (FIR visitation order)
 *  - duplicate path+method declarations dropped parameters/responses (first-wins merge)
 */
class K2ContributionFixesTest {

    @TempDir
    lateinit var tempDir: Path

    private val testFile get() = File(tempDir.toFile(), "openapi.json")

    @Test
    fun `useKDocs=true (default) derives schema descriptions from kdocs`() {
        generateCompilerTest(testFile, loadSourceCodeFrom("KDocs"), PluginConfiguration.createDefault())

        val schema = testFile.parseSpec().components.schemas.getValue("sources.KDocsClass")
        assertThat(schema.description).isEqualTo("This class contains fields with kdocs.")
        assertThat(schema.properties?.get("kdocsProperty")?.description)
            .isEqualTo("This field is called [kdocsProperty].")
    }

    @Test
    fun `useKDocs=false suppresses kdoc-derived descriptions`() {
        generateCompilerTest(
            testFile,
            loadSourceCodeFrom("KDocs"),
            PluginConfiguration.createDefault(useKDocsForDescriptions = false)
        )

        val schema = testFile.parseSpec().components.schemas.getValue("sources.KDocsClass")
        // Before the fix the option was ignored and these would still be populated.
        assertThat(schema.description).isNull()
        // Guard against a vacuous allSatisfy: the class genuinely has properties to check.
        assertThat(schema.properties).isNotNull.isNotEmpty
        assertThat(schema.properties?.values).allSatisfy { assertThat(it.description).isNull() }
    }

    @Test
    fun `paths and http methods are emitted in deterministic sorted order`() {
        generateCompilerTest(testFile, loadSourceCodeFrom("UnorderedRoutes"), PluginConfiguration.createDefault())

        // Jackson preserves JSON object key order into LinkedHashMaps, so key iteration order
        // reflects emission order. Asserting the full key lists checks presence AND order, so a
        // dropped path/method (which raw indexOf would not catch) also fails.
        val paths = testFile.parseSpec().paths
        assertThat(paths.keys.toList()).containsExactly("/v1/aaa", "/v1/zzz")
        assertThat(paths.getValue("/v1/zzz").keys.toList()).containsExactly("delete", "get", "post")
    }

    @Test
    fun `duplicate path+method across modules unions their responses`() {
        generateCompilerTest(testFile, loadSourceCodeFrom("DuplicateEndpointMerge"), PluginConfiguration.createDefault())

        val responses = testFile.parseSpec()
            .paths.getValue("/v1/ping")
            .getValue("get")
            .responses
        // Both modules declared the same get /v1/ping with different response codes; the merge must
        // keep both rather than only the first-encountered one.
        assertThat(responses?.keys).contains("200", "404")
    }
}
