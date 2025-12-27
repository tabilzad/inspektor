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

    // Multi-module support options

    /**
     * Unique identifier for this module in multi-module OpenAPI generation.
     *
     * When set, this module participates in multi-module spec aggregation.
     * The module will either:
     * - Generate a partial spec (if contributor module)
     * - Aggregate partial specs from dependencies (if aggregator module)
     *
     * If null (default), the module operates in standalone mode.
     *
     * **Example:**
     * ```kotlin
     * swagger {
     *     pluginOptions {
     *         moduleId = project.path // e.g., ":feature-users"
     *     }
     * }
     * ```
     */
    var moduleId: String? = null

    /**
     * Whether this module is the aggregator (main server) in multi-module setup.
     *
     * When set to `true`, this module will:
     * - Read partial OpenAPI specs from all dependency JARs
     * - Merge them with locally defined routes
     * - Output the complete OpenAPI specification
     *
     * When set to `false` (default) and moduleId is set, this module will:
     * - Generate a partial OpenAPI spec
     * - Embed it in META-INF/inspektor/openapi-partial.json
     *
     * If set to `"auto"` (string), the plugin will auto-detect based on
     * whether Ktor server engine is on the classpath.
     *
     * **Example:**
     * ```kotlin
     * // Main server module
     * swagger {
     *     pluginOptions {
     *         moduleId = project.path
     *         isAggregator = true
     *     }
     * }
     *
     * // Feature module
     * swagger {
     *     pluginOptions {
     *         moduleId = project.path
     *         isAggregator = false // or omit - this is the default
     *     }
     * }
     * ```
     */
    var isAggregator: Any = false

    /**
     * List of contributor module paths for multi-module aggregation.
     *
     * Only used when `isAggregator = true`. Specifies the Gradle project paths
     * of contributor modules whose partial OpenAPI specs should be merged.
     *
     * The plugin will automatically resolve the partial spec file locations
     * based on each project's build output directory.
     *
     * **Example:**
     * ```kotlin
     * swagger {
     *     pluginOptions {
     *         moduleId = project.path
     *         isAggregator = true
     *         contributors = listOf(":feature-users", ":feature-products", ":feature-orders")
     *     }
     * }
     * ```
     *
     * For each contributor, the plugin looks for the partial spec at:
     * `{contributorBuildDir}/processedResources/{sourceSetName}/META-INF/inspektor/openapi-partial.json`
     */
    var contributors: List<String> = emptyList()
}
