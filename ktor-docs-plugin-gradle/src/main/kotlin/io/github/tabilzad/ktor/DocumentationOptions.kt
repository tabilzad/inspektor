package io.github.tabilzad.ktor

import io.github.tabilzad.ktor.config.InfoConfigBuilder
import io.github.tabilzad.ktor.config.SecurityBuilder
import io.github.tabilzad.ktor.config.TypeOverrideExtension
import io.github.tabilzad.ktor.model.Info
import io.github.tabilzad.ktor.model.SecurityScheme
import org.gradle.api.Action
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
    var polymorphicDiscriminator: String = "type"
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
    fun serialOverrides(action: Action<TypeOverrideExtension>) =
        action.execute(serialOverrides)

    fun security(action: Action<SecurityBuilder>) {
        val builder = SecurityBuilder()
        action.execute(builder)
        builder.build().let {
            securityConfig.addAll(it.scopes)
            securitySchemes.putAll(it.schemes)
        }
    }

    fun info(action: Action<InfoConfigBuilder>) {
        val builder = InfoConfigBuilder()
        action.execute(builder)
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
