package io.github.tabilzad.ktor

import io.github.tabilzad.ktor.TestUtils.loadSourceCodeFrom
import io.github.tabilzad.ktor.output.OpenApiSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Tests for automatic response schema inference from `call.respond*(...)` handler calls.
 * Inference is enabled via [PluginConfiguration.inferResponseSchemas]; explicit `responds<T>()` /
 * `@KtorResponds` always override inferred responses.
 */
class K2ResponseInferenceTest {

    @TempDir
    lateinit var tempDir: Path

    private val testFile get() = File(tempDir.toFile(), "openapi.json")

    private val inferenceOn = PluginConfiguration.createDefault(inferResponseSchemas = true)

    private fun generate(source: String, config: PluginConfiguration = inferenceOn): OpenApiSpec {
        generateCompilerTest(testFile, loadSourceCodeFrom(source), config)
        return testFile.parseSpec()
    }

    @Test
    fun `infers 200 with schema from a single call respond`() {
        val spec = generate("ResponseInference")
        val schema = spec.response("/api/single", "get", "200").schema()
        assertThat(schema?.ref).isEqualTo("#/components/schemas/sources.InferDude")
        assertThat(spec.components.schemas["sources.InferDude"]?.properties?.keys)
            .containsExactlyInAnyOrder("id", "name")
    }

    @Test
    fun `infers explicit status code from HttpStatusCode argument`() {
        val spec = generate("ResponseInference")
        assertThat(spec.response("/api/created", "post", "201")).isNotNull
        assertThat(spec.response("/api/created", "post", "201").schema()?.ref)
            .isEqualTo("#/components/schemas/sources.InferDude")
    }

    @Test
    fun `collects multiple responses from branches`() {
        val spec = generate("ResponseInference")
        val responses = spec.paths.getValue("/api/branches").getValue("get").responses
        assertThat(responses?.keys).contains("200", "404")
        assertThat(spec.response("/api/branches", "get", "200").schema()?.ref)
            .isEqualTo("#/components/schemas/sources.InferDude")
        assertThat(spec.response("/api/branches", "get", "404").schema()?.ref)
            .isEqualTo("#/components/schemas/sources.InferError")
    }

    @Test
    fun `infers array schema for collection responses`() {
        val spec = generate("ResponseInference")
        val schema = spec.response("/api/list", "get", "200").schema()
        assertThat(schema?.type).isEqualTo("array")
        assertThat(schema?.items?.ref).isEqualTo("#/components/schemas/sources.InferDude")
    }

    @Test
    fun `respondBytes infers octet-stream binary schema`() {
        val spec = generate("ResponseInference")

        val bytes = spec.response("/api/bytes", "get", "200")
        val bytesSchema = bytes.schema(contentType = "application/octet-stream")
        assertThat(bytesSchema?.type).isEqualTo("string")
        assertThat(bytesSchema?.format).isEqualTo("binary")
    }

    @Test
    fun `infers a respond wrapped in a nested DSL lambda`() {
        val spec = generate("ResponseInferenceNestedLambda")
        // call.respond is nested inside a custom higher-order DSL (auditing { … }); inference must
        // descend into the lambda and still resolve it.
        assertThat(spec.response("/wrapped", "get", "200").schema()?.ref)
            .isEqualTo("#/components/schemas/sources.WrappedResp")
    }

    @Test
    fun `generic body resolved through an extracted function degrades to no schema`() {
        val spec = generate("ResponseInferenceExtractedFn")
        val generic = spec.response("/generic", "get", "200")
        assertThat(generic).isNotNull
        // unsubstituted type parameter -> response present, schema omitted (not a garbage ref)
        assertThat(generic?.content?.get("application/json")).isEmpty()
    }

    @Test
    fun `explicit KtorResponds annotation wins over inferred and inference fills the gap`() {
        val spec = generate("ResponseInferenceAnnotationPrecedence")
        assertThat(spec.response("/annotated", "get", "200").schema()?.ref)
            .isEqualTo("#/components/schemas/sources.AnnotatedDude")
        assertThat(spec.response("/annotated", "get", "404").schema()?.ref)
            .isEqualTo("#/components/schemas/sources.AnnotationGapError")
    }

    @Test
    fun `respondText infers text-plain string and respondRedirect infers 302 without a body`() {
        val spec = generate("ResponseInference")

        val text = spec.response("/api/text", "get", "200")
        assertThat(text.schema(contentType = "text/plain")?.type).isEqualTo("string")

        val redirect = spec.response("/api/redirect", "get", "302")
        assertThat(redirect).isNotNull
        assertThat(redirect?.content).isNull()
    }

    @Test
    fun `erased Any body yields a response with no schema`() {
        val spec = generate("ResponseInference")
        val erased = spec.response("/api/erased", "get", "200")
        assertThat(erased).isNotNull
        // content type is present but the media type carries no schema
        assertThat(erased?.content?.get("application/json")).isEmpty()
    }

    @Test
    fun `infers through an extracted handler function`() {
        val spec = generate("ResponseInferenceExtractedFn")
        assertThat(spec.response("/extracted", "get", "200").schema()?.ref)
            .isEqualTo("#/components/schemas/sources.ExtractedDude")
    }

    @Test
    fun `reuses the schema generator for sealed and value-class response types`() {
        val spec = generate("ResponseInferenceComplexTypes")

        // Sealed -> oneOf + discriminator (reuses the polymorphic schema path)
        assertThat(spec.response("/types/sealed", "get", "200").schema()?.ref)
            .isEqualTo("#/components/schemas/sources.Shape")
        val shape = spec.components.schemas.getValue("sources.Shape")
        assertThat(shape.oneOf).isNotEmpty
        assertThat(shape.discriminator).isNotNull

        // Value class unwrapped to its underlying type
        assertThat(spec.response("/types/value", "get", "200").schema()?.ref)
            .isEqualTo("#/components/schemas/sources.ValueResp")
        assertThat(spec.components.schemas.getValue("sources.ValueResp").properties?.get("id")?.type)
            .isEqualTo("string")
    }

    @Test
    fun `explicit responds wins over inferred for the same status and inferred fills the gap`() {
        val spec = generate("ResponseInferencePrecedence")

        // 200 declared by responds<ExplicitDude>() must win over the inferred respond(InferredDude)
        assertThat(spec.response("/precedence", "get", "200").schema()?.ref)
            .isEqualTo("#/components/schemas/sources.ExplicitDude")
        // 404 only exists via inference
        assertThat(spec.response("/precedence", "get", "404").schema()?.ref)
            .isEqualTo("#/components/schemas/sources.GapError")
    }

    @Test
    fun `inference disabled (default) produces no inferred responses`() {
        val spec = generate("ResponseInference", config = PluginConfiguration.createDefault())
        assertThat(spec.paths.getValue("/api/single").getValue("get").responses).isNull()
        assertThat(spec.paths.getValue("/api/text").getValue("get").responses).isNull()
    }

    private fun OpenApiSpec.response(path: String, method: String, status: String): OpenApiSpec.ResponseDetails? =
        paths[path]?.get(method)?.responses?.get(status)

    private fun OpenApiSpec.ResponseDetails?.schema(contentType: String = "application/json"): OpenApiSpec.TypeDescriptor? =
        this?.content?.get(contentType)?.get("schema")
}
