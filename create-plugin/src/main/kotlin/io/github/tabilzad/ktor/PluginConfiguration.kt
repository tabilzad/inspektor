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
) {
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
            useKDocsForDescriptions: Boolean? = null
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
                discriminator = initConfig?.discriminator ?: "type"
            )
        }
    }
}
