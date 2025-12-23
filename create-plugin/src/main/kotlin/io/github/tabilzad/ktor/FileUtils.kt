package io.github.tabilzad.ktor

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.tabilzad.ktor.output.OpenApiSpec
import java.io.File

/**
 * Writes the OpenAPI specification to a file, completely replacing any existing content.
 *
 * This is the preferred method for writing specs as it:
 * - Produces deterministic output (same input = same output)
 * - Doesn't accumulate stale data from previous compilations
 * - Properly supports multiple @GenerateOpenApi functions (merged before calling this)
 *
 * The schemas and their properties are sorted alphabetically for consistent output.
 */
internal fun OpenApiSpec.writeFreshTo(configuration: PluginConfiguration) {
    val file = File(configuration.filePath).apply {
        parentFile?.mkdirs()
    }

    val sorted = this.copy(
        components = components.copy(
            schemas = components.schemas.toSortedMap()
                .mapValues {
                    it.value.copy(properties = it.value.properties?.toSortedMap())
                }
        )
    )

    file.writeText(getJacksonBy(configuration.format).writeValueAsString(sorted))
}

fun getJacksonBy(format: String): ObjectMapper = when (format) {
    "yaml" -> ObjectMapper(YAMLFactory()).registerKotlinModule()
    else -> jacksonObjectMapper()
}.apply {
    enable(SerializationFeature.INDENT_OUTPUT)
    setSerializationInclusion(JsonInclude.Include.NON_NULL)
}
