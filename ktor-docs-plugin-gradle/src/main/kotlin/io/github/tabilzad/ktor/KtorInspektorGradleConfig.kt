package io.github.tabilzad.ktor

import org.gradle.api.Action
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
    fun documentation(action: Action<DocumentationOptions>) = action.execute(_documentation)

    /** swagger { pluginOptions { … } } */
    val pluginOptions: PluginOptions get() = _pluginOptions
    fun pluginOptions(action: Action<PluginOptions>) = action.execute(_pluginOptions)
}

open class PluginOptions @Inject constructor(
    objects: ObjectFactory
) {
    var enabled: Boolean = true
    var saveInBuild: Boolean = true
    var filePath: String? = null
    var format: String = "json"
}
