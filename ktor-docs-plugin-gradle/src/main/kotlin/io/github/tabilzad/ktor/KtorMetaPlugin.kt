package io.github.tabilzad.ktor

import com.vdurmont.semver4j.Semver
import io.github.tabilzad.ktor.model.ConfigInput
import io.github.tabilzad.ktor.model.PartialSpecLocation
import kotlinx.serialization.json.Json
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.toKotlinVersion
import java.io.File
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

const val PLUGIN_ID = "io.github.tabilzad.inspektor"

@Suppress("MagicNumber")
private val COMPATIBLE_VERSIONS = setOf(KotlinVersion(2, 4, 0))

class KtorMetaPlugin @Inject constructor(
    private val objects: ObjectFactory
) : KotlinCompilerPluginSupportPlugin {

    override fun getCompilerPluginId() = PLUGIN_ID
    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(
            groupId = "io.github.tabilzad.inspektor",
            artifactId = "ktor-docs-plugin",
            version = inspektorVersion
        )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        val project = kotlinCompilation.target.project
        checkKotlinVersionCompatibility(project)

        val hasKtorMetaPlugin = project.plugins.hasPlugin(KtorMetaPlugin::class.java)
        val isTestCompilation = kotlinCompilation.name.contains("test", ignoreCase = true)

        // Skip test compilations — OpenAPI spec generation is only meaningful for main compilations
        return hasKtorMetaPlugin && !isTestCompilation
    }

    override fun apply(target: Project) {
        target.extensions.create(
            "swagger",
            KtorInspectorGradleConfig::class.java,
            objects
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Suppress("LongMethod")
    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val swaggerExtension = project.extensions.getByType(KtorInspectorGradleConfig::class.java)

        kotlinCompilation.dependencies {
            compileOnly("io.github.tabilzad.inspektor:ktor-docs-plugin:$inspektorVersion")
            implementation("io.github.tabilzad.inspektor:annotations:$inspektorVersion")
        }

        val format = swaggerExtension.pluginOptions.format

        // Canonical output file lives under build/openapi/ — a fixed, build-relative path
        // that Gradle can relocate across machines for remote build cache compatibility.
        val canonicalOutputDir = File(project.layout.buildDirectory.get().asFile, "openapi")
        val canonicalOutputFile = File(canonicalOutputDir, "openapi.$format")

        // The user's desired destination, which may differ from the canonical location.
        val userDesiredFile = with(swaggerExtension.pluginOptions) {
            getUserDesiredOutputFile(
                filePath = filePath,
                saveInBuild = saveInBuild,
                // This is build/processedResources/release,
                // do we want /build instead? kotlinCompilation.target.project.buildDir.absolutePath
                buildPath = kotlinCompilation.output.resourcesDir.absolutePath,
                modulePath = project.projectDir.absolutePath,
                format = format
            )
        }

        // If the user wants the file somewhere other than the canonical location,
        // register a copy task to move it there after compilation.
        if (userDesiredFile != canonicalOutputFile) {
            val compilationName = kotlinCompilation.name.replaceFirstChar { it.uppercase() }
            val copyTaskName = "copyOpenApiSpec$compilationName"

            val copyTask = project.tasks.register(copyTaskName, Copy::class.java) { copy ->
                copy.description = "Copies generated OpenAPI spec to configured destination"
                copy.from(canonicalOutputFile)
                copy.into(userDesiredFile.parentFile)
                copy.rename { userDesiredFile.name }
                copy.onlyIf { canonicalOutputFile.exists() }
            }
            kotlinCompilation.compileTaskProvider.configure { it.finalizedBy(copyTask) }
        }

        val initialConfig = ConfigInput(
            securityConfig = swaggerExtension.documentation.getSecurityConfig(),
            securitySchemes = swaggerExtension.documentation.getSecuritySchemes(),
            info = swaggerExtension.documentation.getInfo(),
            overrides = swaggerExtension.documentation.serialOverrides.getOverrides().map { it.toConfigInput() },
            discriminator = swaggerExtension.documentation.polymorphicDiscriminator,
            commonHeaders = swaggerExtension.documentation.getCommonHeaders()
        )

        // Serialize the config to use as an input hash for Gradle caching
        val initialConfigJson = Json.encodeToString<ConfigInput>(initialConfig)

        // Validate regeneration mode
        val regenerationMode = swaggerExtension.pluginOptions.regenerationMode.lowercase()
        require(regenerationMode in listOf("strict", "safe", "fast")) {
            "Invalid regenerationMode '${swaggerExtension.pluginOptions.regenerationMode}'. " +
                    "Must be one of: strict, safe, fast"
        }

        // Configure Gradle task inputs/outputs based on regeneration mode
        kotlinCompilation.compileTaskProvider.configure { task ->
            // Register the canonical output directory so Gradle can track it for
            // up-to-date checks and remote build cache. Using a directory under build/
            // ensures the path is build-relative and relocatable across machines.
            task.outputs.dir(canonicalOutputDir)
                .withPropertyName("openApiSpec")

            // Register plugin configuration as inputs so config changes trigger regeneration.
            // This applies to all modes - changing swagger { } config should always regenerate.
            task.inputs.property("swagger.enabled", swaggerExtension.pluginOptions.enabled)
            task.inputs.property("swagger.format", swaggerExtension.pluginOptions.format)
            task.inputs.property("swagger.regenerationMode", regenerationMode)
            task.inputs.property("swagger.generateRequestSchemas",
                swaggerExtension.documentation.generateRequestSchemas)
            task.inputs.property("swagger.hideTransientFields",
                swaggerExtension.documentation.hideTransientFields)
            task.inputs.property("swagger.hidePrivateAndInternalFields",
                swaggerExtension.documentation.hidePrivateAndInternalFields)
            task.inputs.property("swagger.deriveFieldRequirementFromTypeNullability",
                swaggerExtension.documentation.deriveFieldRequirementFromTypeNullability)
            task.inputs.property("swagger.useKDocsForDescriptions",
                swaggerExtension.documentation.useKDocsForDescriptions)
            task.inputs.property("swagger.inferResponseSchemas",
                swaggerExtension.documentation.inferResponseSchemas)
            task.inputs.property("swagger.servers",
                swaggerExtension.documentation.servers.joinToString(","))
            task.inputs.property("swagger.initialConfig", initialConfigJson.hashCode())

            // Apply regeneration mode strategy
            when (regenerationMode) {
                "strict" -> {
                    // STRICT MODE: Always regenerate on every compilation.
                    // Guarantees correctness but disables incremental compilation benefits.
                    // Recommended for CI/CD and release builds.
                    if (task is KotlinCompile) {
                        task.incremental = false
                    } else {
                        task.outputs.upToDateWhen { false }
                    }
                }

                "safe" -> {
                    // SAFE MODE: Track source files containing @GenerateOpenApi as inputs.
                    // Regenerates when any annotated file changes. Faster than strict for
                    // changes to unrelated files, but may miss body-only changes to route
                    // functions within the same module.
                    val openApiSourceFiles = findOpenApiAnnotatedFiles(kotlinCompilation)
                    if (openApiSourceFiles.isNotEmpty()) {
                        task.inputs.files(openApiSourceFiles)
                            .withPropertyName("openApiSourceFiles")
                            .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
                    }
                }

                "fast" -> {
                    // FAST MODE: Trust Kotlin's incremental compilation completely.
                    // Fastest builds but may produce incomplete specs when only some
                    // source files are recompiled. Use for rapid local development only.
                    // No additional input tracking beyond standard Kotlin compilation.
                }
            }
        }

        // Determine multi-module configuration
        val moduleId = swaggerExtension.pluginOptions.moduleId
        val isAggregator = swaggerExtension.pluginOptions.isAggregator

        // Resources path for partial spec output (only for contributors)
        val resourcesPath = if (moduleId != null && !isAggregator) {
            kotlinCompilation.output.resourcesDir.absolutePath
        } else {
            null
        }

        val subpluginOptions = mutableListOf(
            SubpluginOption(
                key = "enabled",
                value = swaggerExtension.pluginOptions.enabled.toString()
            ),
            SubpluginOption(
                key = "generateRequestSchemas",
                value = swaggerExtension.documentation.generateRequestSchemas.toString()
            ),
            SubpluginOption(
                key = "hideTransientFields",
                value = swaggerExtension.documentation.hideTransientFields.toString()
            ),
            SubpluginOption(
                key = "hidePrivateAndInternalFields",
                value = swaggerExtension.documentation.hidePrivateAndInternalFields.toString()
            ),
            SubpluginOption(
                key = "deriveFieldRequirementFromTypeNullability",
                value = swaggerExtension.documentation.deriveFieldRequirementFromTypeNullability.toString()
            ),
            SubpluginOption(
                key = "servers",
                value = swaggerExtension.documentation.servers.joinToString("||")
            ),
            SubpluginOption(
                key = "useKDocs",
                value = swaggerExtension.documentation.useKDocsForDescriptions.toString()
            ),
            SubpluginOption(
                key = "inferResponseSchemas",
                value = swaggerExtension.documentation.inferResponseSchemas.toString()
            ),
            SubpluginOption(
                key = "format",
                value = swaggerExtension.pluginOptions.format
            ),
            InternalSubpluginOption(
                key = "filePath",
                value = canonicalOutputFile.path
            ),
            SubpluginOption(
                key = "initialConfig",
                value = Base64.encode(initialConfigJson.toByteArray())
            )
        )

        subpluginOptions.addAll(
            multiModuleOptions(project, swaggerExtension, kotlinCompilation, moduleId, isAggregator, resourcesPath)
        )

        return project.provider { subpluginOptions }
    }

    /**
     * Builds the multi-module subplugin options when a moduleId is configured: the module's
     * identity/role, the resources path for contributors, and — for aggregators — the resolved
     * partial spec paths of the configured contributor modules.
     */
    @Suppress("LongParameterList")
    private fun multiModuleOptions(
        project: Project,
        swaggerExtension: KtorInspectorGradleConfig,
        kotlinCompilation: KotlinCompilation<*>,
        moduleId: String?,
        isAggregator: Boolean,
        resourcesPath: String?
    ): List<SubpluginOption> {
        if (moduleId == null) return emptyList()

        return buildList {
            add(SubpluginOption(key = "moduleId", value = moduleId))
            add(SubpluginOption(key = "isAggregator", value = isAggregator.toString()))
            if (resourcesPath != null) {
                add(SubpluginOption(key = "resourcesPath", value = resourcesPath))
            }

            // For aggregator modules, resolve contributor partial spec paths
            if (isAggregator && swaggerExtension.pluginOptions.contributors.isNotEmpty()) {
                val partialSpecPaths = resolveContributorPartialSpecPaths(
                    project,
                    swaggerExtension.pluginOptions.contributors,
                    kotlinCompilation
                )
                if (partialSpecPaths.isNotEmpty()) {
                    add(
                        SubpluginOption(
                            key = "partialSpecPaths",
                            value = partialSpecPaths.joinToString("||")
                        )
                    )
                }
            }
        }
    }

    /**
     * Resolves paths to partial OpenAPI spec files from contributor modules.
     *
     * For each contributor project path, this method resolves the project and
     * constructs the expected path to its partial spec file based on the
     * compilation's resources output directory structure.
     *
     * @param project The current (aggregator) project
     * @param contributors List of Gradle project paths (e.g., ":feature-users")
     * @param kotlinCompilation The current compilation context
     * @return List of absolute paths to partial spec files
     */
    private fun resolveContributorPartialSpecPaths(
        project: Project,
        contributors: List<String>,
        kotlinCompilation: KotlinCompilation<*>
    ): List<String> {
        val partialSpecRelativePath = PartialSpecLocation.FULL_PATH

        return contributors.mapNotNull { contributorPath ->
            try {
                val contributorProject = project.rootProject.findProject(contributorPath)
                if (contributorProject == null) {
                    project.logger.warn(
                        "[inspektor] Contributor project '$contributorPath' not found. " +
                                "Ensure the project path is correct and the project is included in the build."
                    )
                    return@mapNotNull null
                }

                // Construct the path to the partial spec based on the build directory
                // The partial spec is written to: {buildDir}/processedResources/{variant}/META-INF/inspektor/openapi-partial.json
                // We use the compilation name to determine the variant (e.g., "main", "jvm")
                val compilationName = kotlinCompilation.compilationName
                val resourcesDir = File(
                    contributorProject.layout.buildDirectory.asFile.get(),
                    "processedResources/$compilationName"
                )
                val partialSpecFile = File(resourcesDir, partialSpecRelativePath)

                partialSpecFile.absolutePath
            } catch (e: Exception) {
                project.logger.warn(
                    "[inspektor] Failed to resolve contributor '$contributorPath': ${e.message}"
                )
                null
            }
        }
    }

    private fun checkKotlinVersionCompatibility(project: Project) {
        val projectsKotlinVersion = KotlinToolingVersion(project.getKotlinPluginVersion()).toKotlinVersion()
        val compatibleVersions = COMPATIBLE_VERSIONS + Semver(kotlinVersion).toKotlinVersion()
        val inCompatibleVersion = compatibleVersions
            .any { projectsKotlinVersion.isAtLeast(it.major, it.minor, it.patch) }
        if (!inCompatibleVersion) {
            project.logger.warn(
                "[inspektor] Kotlin plugin version $projectsKotlinVersion may be incompatible." +
                        " Supported range: $compatibleVersions"
            )
        }
    }

    private fun getUserDesiredOutputFile(
        filePath: String?,
        saveInBuild: Boolean,
        buildPath: String,
        modulePath: String,
        format: String,
    ): File {
        val directoryPath = when {
            filePath != null -> filePath
            saveInBuild -> "$buildPath/openapi"
            else -> "${getProjectResourcesDirectory(modulePath)}/openapi"
        }
        val directory = File(directoryPath)
        return File(directory, "openapi.$format")
    }

    private fun getProjectResourcesDirectory(modulePath: String): File {
        val mainModule = File("$modulePath/src/main")
        val resourcesDir = mainModule.listFiles()?.find {
            it.name in listOf("res", "resources")
        } ?: throw IllegalArgumentException(
            "Couldn't find resources directory to save openapi file to. Searched in $modulePath"
        )
        return resourcesDir
    }

    private fun Semver.toKotlinVersion(): KotlinVersion = KotlinVersion(this.major, this.minor, this.patch)

    /**
     * Finds all Kotlin source files that contain the @GenerateOpenApi annotation.
     * Used in "safe" regeneration mode to track annotated files as Gradle inputs.
     *
     * This function scans source files for the annotation text, checking for:
     * - Simple annotation: @GenerateOpenApi
     * - Fully qualified: @io.github.tabilzad.ktor.annotations.GenerateOpenApi
     *
     * Note: This is a text-based scan, not a semantic analysis. It may have false
     * positives (e.g., annotation in comments) but this is acceptable for input tracking.
     */
    private fun findOpenApiAnnotatedFiles(kotlinCompilation: KotlinCompilation<*>): List<File> {
        return kotlinCompilation.allKotlinSourceSets
            .flatMap { sourceSet -> sourceSet.kotlin.srcDirs }
            .filter { it.exists() && it.isDirectory }
            .flatMap { srcDir ->
                srcDir.walkTopDown()
                    .filter { file ->
                        file.isFile &&
                                file.extension == "kt" &&
                                file.canRead()
                    }
                    .filter { file ->
                        try {
                            val content = file.readText()
                            // Check for annotation in various forms
                            content.contains("@GenerateOpenApi") ||
                                    content.contains("@io.github.tabilzad.ktor.annotations.GenerateOpenApi")
                        } catch (e: Exception) {
                            // If we can't read the file, skip it
                            false
                        }
                    }
                    .toList()
            }
    }
}
