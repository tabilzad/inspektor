package io.github.tabilzad.ktor

import io.github.tabilzad.ktor.config.InfoConfigBuilder
import io.github.tabilzad.ktor.config.SecurityBuilder
import io.github.tabilzad.ktor.model.Info
import io.github.tabilzad.ktor.model.SecurityScheme

open class DocumentationOptions(
    var generateRequestSchemas: Boolean = true,
    var hideTransientFields: Boolean = true,
    var hidePrivateAndInternalFields: Boolean = true,
    var deriveFieldRequirementFromTypeNullability: Boolean = true,
    var useKDocsForDescriptions: Boolean = true,
    var servers: List<String> = emptyList(),
) {

    @Deprecated("Please use info { } extension instead ")
    var docsTitle: String = "Open API Specification"
        set(value) {
            field = value
            info = info.copy(title = value)
        }

    @Deprecated("Please use info { } extension instead ")
    var docsDescription: String = "Generated using Ktor Docs Plugin"
        set(value) {
            field = value
            info = info.copy(description = value)
        }

    @Deprecated("Please use info { } extension instead ")
    var docsVersion: String = "1.0.0"
        set(value) {
            field = value
            info = info.copy(version = value)
        }

    private var info = Info(title = docsTitle, description = docsDescription, version = docsVersion)
    private val securityConfig: MutableList<Map<String, List<String>>> = mutableListOf()
    private val securitySchemes: MutableMap<String, SecurityScheme> = mutableMapOf()

    fun security(block: SecurityBuilder.() -> Unit) {
        val builder = SecurityBuilder()
        builder.block()
        builder.build().let {
            securityConfig.addAll(it.scopes)
            securitySchemes.putAll(it.schemes)
        }
    }

    fun info(block: InfoConfigBuilder.() -> Unit) {
        val builder = InfoConfigBuilder()
        builder.block()
        val infoBlock = builder.build()
        info = info.copy(
            title = infoBlock.title ?: info.title,
            description = infoBlock.description ?: info.description,
            version = infoBlock.version ?: info.version,
            license = infoBlock.license,
            contact = infoBlock.contact
        )
    }

    internal fun getInfo() = info

    internal fun getSecurityConfig(): List<Map<String, List<String>>> = securityConfig.toList()

    internal fun getSecuritySchemes(): Map<String, SecurityScheme> = securitySchemes.toMap()
}

open class PluginOptions(
    var enabled: Boolean = true,
    var saveInBuild: Boolean = true,
    var filePath: String? = null,
    var format: String = "json"
)

open class KtorInspectorGradleConfig(
    var documentation: DocumentationOptions = DocumentationOptions(),
    var pluginOptions: PluginOptions = PluginOptions(),
)
