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

    /**
     * Controls how OpenAPI spec regeneration interacts with Gradle's incremental compilation.
     *
     * ## Available Modes
     *
     * ### "strict" (default)
     * Always regenerates the OpenAPI spec on every compilation. This guarantees the spec
     * is always complete and correct, but disables incremental compilation benefits.
     *
     * **When to use:** CI/CD pipelines, release builds, or whenever spec correctness is critical.
     *
     * ### "safe"
     * Tracks source files containing `@GenerateOpenApi` as explicit Gradle inputs. The spec
     * is regenerated whenever any of these annotated files change.
     *
     * **Trade-off:** Faster than "strict" for changes to unrelated files, but may miss updates
     * when route functions (called by `@GenerateOpenApi` functions but not annotated themselves)
     * have body-only changes within the same module. Cross-module changes are handled correctly.
     *
     * **When to use:** Local development when you primarily edit `@GenerateOpenApi` files directly.
     *
     * ### "fast"
     * Trusts Kotlin's incremental compilation completely. Only regenerates when Gradle
     * determines the compilation task needs to run.
     *
     * **Trade-off:** Fastest builds, but may produce incomplete specs when only some source
     * files are recompiled. The spec will only contain routes from recompiled files.
     *
     * **When to use:** Rapid local iteration where build speed is prioritized over spec
     * completeness. Not recommended for any builds where the spec will be used or deployed.
     *
     * ## Example Configuration
     * ```kotlin
     * swagger {
     *     pluginOptions {
     *         regenerationMode = "strict"  // default - always correct
     *         // regenerationMode = "safe" // balanced - tracks @GenerateOpenApi files
     *         // regenerationMode = "fast" // fastest - may be incomplete
     *     }
     * }
     * ```
     */
    var regenerationMode: String = "strict"
}
