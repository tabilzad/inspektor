package io.github.tabilzad.ktor

import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/**
 * Root of swagger { … }
 */
open class KtorInspectorGradleConfig @Inject constructor(
    objects: ObjectFactory
) {
    // eagerly instantiate
    private val _documentation = objects.newInstance(DocumentationOptions::class.java, objects)
    private val _pluginOptions = objects.newInstance(PluginOptions::class.java)

    /** swagger { documentation { … } } */
    val documentation: DocumentationOptions get() = _documentation
    fun documentation(block: DocumentationOptions.() -> Unit) = _documentation.apply(block)

    /** swagger { pluginOptions { … } } */
    val pluginOptions: PluginOptions get() = _pluginOptions
    fun pluginOptions(block: PluginOptions.() -> Unit) = _pluginOptions.apply(block)
}

open class PluginOptions @Inject constructor(
    objects: ObjectFactory
) {
    var enabled: Boolean = true
    var saveInBuild: Boolean = true
    var filePath: String? = null
    var format: String = "json"
}
