package io.github.tabilzad.ktor

import io.github.tabilzad.ktor.model.ConfigInput
import io.github.tabilzad.ktor.model.Info

// Internal
internal data class PluginConfiguration(
    val isEnabled: Boolean,
    val format: String,
    val filePath: String,
    val requestBody: Boolean,
    val hideTransients: Boolean,
    val hidePrivateFields: Boolean,
    val servers: List<String>,
    val initConfig: ConfigInput,
    val deriveFieldRequirementFromTypeNullability: Boolean,
    val useKDocsForDescriptions: Boolean,
    val discriminator: String,
    // Multi-module support
    val moduleId: String?,
    val isAggregator: Boolean,
    val resourcesPath: String?,
    val partialSpecPaths: List<String>,
) {
    /**
     * Whether this module should generate a partial spec for multi-module aggregation.
     * A module is a contributor if it has a moduleId but is not an aggregator.
     */
    val isContributor: Boolean
        get() = moduleId != null && !isAggregator

    companion object {
        fun createDefault(
            isEnabled: Boolean? = null,
            format: String? = null,
            filePath: String? = null,
            requestBody: Boolean? = null,
            hideTransients: Boolean? = null,
            hidePrivateFields: Boolean? = null,
            servers: List<String>? = null,
            initConfig: ConfigInput? = null,
            deriveFieldRequirementFromTypeNullability: Boolean? = null,
            useKDocsForDescriptions: Boolean? = null,
            moduleId: String? = null,
            isAggregator: Boolean? = null,
            resourcesPath: String? = null,
            partialSpecPaths: List<String>? = null
        ): PluginConfiguration {
            val defaultTitle = "Open API Specification"
            val defaultVersion = "1.0.0"
            return PluginConfiguration(
                isEnabled = isEnabled ?: true,
                format = format ?: "yaml",
                filePath = filePath ?: "openapi.yaml",
                requestBody = requestBody ?: true,
                hideTransients = hideTransients ?: true,
                hidePrivateFields = hidePrivateFields ?: true,
                deriveFieldRequirementFromTypeNullability = deriveFieldRequirementFromTypeNullability ?: true,
                servers = servers ?: emptyList(),
                initConfig = initConfig ?: ConfigInput(
                    emptyList(),
                    emptyMap(),
                    Info(
                        title = defaultTitle,
                        description = "",
                        version = defaultVersion,
                        contact = null,
                        license = null
                    )
                ),
                useKDocsForDescriptions = useKDocsForDescriptions ?: true,
                discriminator = initConfig?.discriminator ?: "type",
                moduleId = moduleId,
                isAggregator = isAggregator ?: false,
                resourcesPath = resourcesPath,
                partialSpecPaths = partialSpecPaths ?: emptyList()
            )
        }
    }
}
