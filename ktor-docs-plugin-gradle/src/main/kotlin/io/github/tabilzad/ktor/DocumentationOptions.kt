package io.github.tabilzad.ktor

import io.github.tabilzad.ktor.config.InfoConfigBuilder
import io.github.tabilzad.ktor.config.SecurityBuilder
import io.github.tabilzad.ktor.config.TypeOverrideExtension
import io.github.tabilzad.ktor.model.Info
import io.github.tabilzad.ktor.model.SecurityScheme
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/**
 * swagger {
 *   documentation { … }
 * }
 */
open class DocumentationOptions @Inject constructor(
    objects: ObjectFactory
) {
    var generateRequestSchemas: Boolean = true
    var hideTransientFields: Boolean = true
    var hidePrivateAndInternalFields: Boolean = true
    var deriveFieldRequirementFromTypeNullability: Boolean = true
    var useKDocsForDescriptions: Boolean = true
    var servers: List<String> = emptyList()

    private var info = Info()
    private val securityConfig: MutableList<Map<String, List<String>>> = mutableListOf()
    private val securitySchemes: MutableMap<String, SecurityScheme> = mutableMapOf()

    internal val serialOverrides: TypeOverrideExtension =
        objects.newInstance(TypeOverrideExtension::class.java, objects)

    /**
     * swagger {
     *   documentation {
     *     openApi { … }
     *   }
     * }
     */
    fun serialOverrides(configure: TypeOverrideExtension.() -> Unit) =
        serialOverrides.apply(configure)

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
