package io.github.tabilzad.ktor

import io.github.tabilzad.ktor.SwaggerConfigurationKeys.ARG_DERIVE_PROP_REQ
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.ARG_ENABLED
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.ARG_FORMAT
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.ARG_HIDE_PRIVATE
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.ARG_HIDE_TRANSIENTS
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.ARG_INIT_CONFIG
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.ARG_IS_AGGREGATOR
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.ARG_KDOCS
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.ARG_MODULE_ID
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.ARG_PARTIAL_SPEC_PATHS
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.ARG_PATH
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.ARG_REQUEST_FEATURE
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.ARG_RESOURCES_PATH
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.ARG_SERVERS
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.OPTION_DERIVE_PROP_REQ
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.OPTION_FORMAT
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.OPTION_HIDE_PRIVATE
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.OPTION_HIDE_TRANSIENT
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.OPTION_INIT_CONFIG
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.OPTION_IS_AGGREGATOR
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.OPTION_IS_ENABLED
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.OPTION_MODULE_ID
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.OPTION_PATH
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.OPTION_REQUEST_BODY
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.OPTION_RESOURCES_PATH
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.OPTION_SERVERS
import io.github.tabilzad.ktor.SwaggerConfigurationKeys.OPTION_USE_KDOCS
import io.github.tabilzad.ktor.model.ConfigInput
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object SwaggerConfigurationKeys {
    const val OPTION_IS_ENABLED = "enabled"
    const val OPTION_PATH = "filePath"
    const val OPTION_REQUEST_BODY = "generateRequestSchemas"
    const val OPTION_HIDE_TRANSIENT = "hideTransientFields"
    const val OPTION_HIDE_PRIVATE = "hidePrivateAndInternalFields"
    const val OPTION_DERIVE_PROP_REQ = "deriveFieldRequirementFromTypeNullability"
    const val OPTION_USE_KDOCS = "useKDocs"
    const val OPTION_SERVERS = "servers"
    const val OPTION_INIT_CONFIG = "initialConfig"
    const val OPTION_FORMAT = "format"

    // Multi-module support options
    const val OPTION_MODULE_ID = "moduleId"
    const val OPTION_IS_AGGREGATOR = "isAggregator"
    const val OPTION_RESOURCES_PATH = "resourcesPath"
    const val OPTION_PARTIAL_SPEC_PATHS = "partialSpecPaths"

    val ARG_ENABLED = CompilerConfigurationKey.create<Boolean>(OPTION_IS_ENABLED)
    val ARG_PATH = CompilerConfigurationKey.create<String>(OPTION_PATH)
    val ARG_REQUEST_FEATURE = CompilerConfigurationKey.create<Boolean>(OPTION_REQUEST_BODY)
    val ARG_HIDE_TRANSIENTS = CompilerConfigurationKey.create<Boolean>(OPTION_HIDE_TRANSIENT)
    val ARG_HIDE_PRIVATE = CompilerConfigurationKey.create<Boolean>(OPTION_HIDE_PRIVATE)
    val ARG_DERIVE_PROP_REQ = CompilerConfigurationKey.create<Boolean>(OPTION_DERIVE_PROP_REQ)
    val ARG_FORMAT = CompilerConfigurationKey.create<String>(OPTION_FORMAT)
    val ARG_SERVERS = CompilerConfigurationKey.create<List<String>>(OPTION_SERVERS)
    val ARG_INIT_CONFIG = CompilerConfigurationKey.create<ConfigInput>(OPTION_INIT_CONFIG)
    val ARG_KDOCS = CompilerConfigurationKey.create<Boolean>(OPTION_USE_KDOCS)

    // Multi-module support keys
    val ARG_MODULE_ID = CompilerConfigurationKey.create<String>(OPTION_MODULE_ID)
    val ARG_IS_AGGREGATOR = CompilerConfigurationKey.create<Boolean>(OPTION_IS_AGGREGATOR)
    val ARG_RESOURCES_PATH = CompilerConfigurationKey.create<String>(OPTION_RESOURCES_PATH)
    val ARG_PARTIAL_SPEC_PATHS = CompilerConfigurationKey.create<List<String>>(OPTION_PARTIAL_SPEC_PATHS)
}

@ExperimentalEncodingApi
@OptIn(ExperimentalCompilerApi::class)
class KtorDocsCommandLineProcessor : CommandLineProcessor {
    companion object {
        val isEnabled = CliOption(
            OPTION_IS_ENABLED,
            "Should plugin run",
            "Is plugin enabled",
            false
        )
        val pathOption = CliOption(
            OPTION_PATH,
            "Custom Absolute Path",
            "Custom absolute path for generated specification",
            true
        )
        val requestSchema = CliOption(
            OPTION_REQUEST_BODY,
            "Request Schema",
            "Enable request body definitions",
            false
        )
        val hideTransientFields = CliOption(
            OPTION_HIDE_TRANSIENT,
            "Hide Transient fields",
            "Hide fields annotated with @Transient in body definitions",
            false
        )
        val hidePrivateAndInternalFields = CliOption(
            OPTION_HIDE_PRIVATE,
            "Hide private or internal fields",
            "Hide private or internal fields in body definitions",
            false
        )
        val derivePropRequirement = CliOption(
            OPTION_DERIVE_PROP_REQ,
            "true sets automation resolution, false sets to explicit",
            "Automatically derive object properties' requirement from the type nullability",
            false
        )
        val useKDocs = CliOption(
            OPTION_USE_KDOCS,
            "true opts for using kdocs for schema descriptions",
            "Resolve schema descriptions from kdocs",
            false
        )
        val formatOption = CliOption(
            OPTION_FORMAT,
            "Specification format",
            "Either yaml or json, json by default",
            false
        )
        val serverUrls = CliOption(
            OPTION_SERVERS,
            "Swagger server urls",
            "Comma separated list of server urls to include in openapi.yaml",
            // should try collecting occurrences instead of parsing lists
            allowMultipleOccurrences = true,
            required = false
        )
        val initConfig = CliOption(
            OPTION_INIT_CONFIG,
            "Swagger global security configuration",
            "Security config in the form of base64 serialized JSON of List<Map<String, List<String>>>",
            allowMultipleOccurrences = false,
            required = false
        )

        // Multi-module support options
        val moduleIdOption = CliOption(
            OPTION_MODULE_ID,
            "Module identifier",
            "Unique identifier for this module (typically the Gradle project path)",
            required = false
        )
        val isAggregatorOption = CliOption(
            OPTION_IS_AGGREGATOR,
            "Is aggregator module",
            "Whether this module is the aggregator (main server) or a contributor module",
            required = false
        )
        val resourcesPathOption = CliOption(
            OPTION_RESOURCES_PATH,
            "Resources output path",
            "Path where partial spec resources should be written",
            required = false
        )
        val partialSpecPathsOption = CliOption(
            SwaggerConfigurationKeys.OPTION_PARTIAL_SPEC_PATHS,
            "Partial spec paths",
            "Paths to partial spec files from contributor modules (pipe-separated)",
            allowMultipleOccurrences = false,
            required = false
        )
    }

    override val pluginId: String
        get() = "io.github.tabilzad.inspektor"

    override val pluginOptions: Collection<AbstractCliOption>
        get() = listOf(
            isEnabled,
            pathOption,
            requestSchema,
            hideTransientFields,
            hidePrivateAndInternalFields,
            derivePropRequirement,
            formatOption,
            serverUrls,
            useKDocs,
            initConfig,
            moduleIdOption,
            isAggregatorOption,
            resourcesPathOption,
            partialSpecPathsOption
        )

    @Suppress("CyclomaticComplexMethod")
    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration
    ) {
        when (option) {
            isEnabled -> configuration.put(ARG_ENABLED, value.toBooleanStrictOrNull() ?: true)

            pathOption -> configuration.put(ARG_PATH, value)

            formatOption -> configuration.put(ARG_FORMAT, value)

            derivePropRequirement -> configuration.put(ARG_DERIVE_PROP_REQ, value.toBooleanStrictOrNull() ?: true)

            requestSchema -> configuration.put(ARG_REQUEST_FEATURE, value.toBooleanStrictOrNull() ?: true)

            hideTransientFields -> configuration.put(ARG_HIDE_TRANSIENTS, value.toBooleanStrictOrNull() ?: true)

            hidePrivateAndInternalFields -> configuration.put(ARG_HIDE_PRIVATE, value.toBooleanStrictOrNull() ?: true)

            useKDocs -> configuration.put(ARG_KDOCS, value.toBooleanStrictOrNull() ?: true)

            serverUrls -> configuration.put(ARG_SERVERS, value.split("||").filter { it.isNotBlank() })

            initConfig -> configuration.put(
                ARG_INIT_CONFIG,
                Base64.decode(value).toString(Charsets.UTF_8).let { decodedJson ->
                    Json.decodeFromString<ConfigInput>(decodedJson)
                }
            )

            // Multi-module support options
            moduleIdOption -> configuration.put(ARG_MODULE_ID, value)

            isAggregatorOption -> configuration.put(ARG_IS_AGGREGATOR, value.toBooleanStrictOrNull() ?: false)

            resourcesPathOption -> configuration.put(ARG_RESOURCES_PATH, value)

            partialSpecPathsOption -> configuration.put(
                ARG_PARTIAL_SPEC_PATHS,
                value.split("||").filter { it.isNotBlank() }
            )

            else -> throw IllegalArgumentException("Unexpected config option ${option.optionName}")
        }
    }
}
