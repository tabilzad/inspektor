package io.github.tabilzad.ktor

import com.vdurmont.semver4j.Semver
import io.github.tabilzad.ktor.model.ConfigInput
import kotlinx.serialization.json.Json
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.toKotlinVersion
import java.io.File
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

const val PLUGIN_ID = "io.github.tabilzad.inspektor"

@Suppress("MagicNumber")
private val COMPATIBLE_VERSIONS = listOf(KotlinVersion(2, 3, 0))

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

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>) = kotlinCompilation.target.project.run {
        checkKotlinVersionCompatibility(this)
        plugins.hasPlugin(KtorMetaPlugin::class.java)
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

        val openApiOutputFile = with(swaggerExtension.pluginOptions) {
            getOpenApiOutputFile(
                filePath = filePath,
                saveInBuild = saveInBuild,
                // This is build/processedResources/release,
                // do we want /build instead? kotlinCompilation.target.project.buildDir.absolutePath
                buildPath = kotlinCompilation.output.resourcesDir.absolutePath,
                modulePath = project.projectDir.absolutePath,
                format = format
            )
        }

        kotlinCompilation.compileTaskProvider.configure { task ->
            if (!openApiOutputFile.exists()) {
                task.outputs.upToDateWhen { false }
            }
            // This should be a more correct way to do this:
            // task.outputs.file(openApiOutputFile)
            // However, setting a KotlinCompile output task.outputs.file("foo") always creates a *directory* named foo
        }

        val initialConfig = ConfigInput(
            securityConfig = swaggerExtension.documentation.getSecurityConfig(),
            securitySchemes = swaggerExtension.documentation.getSecuritySchemes(),
            info = swaggerExtension.documentation.getInfo(),
            overrides = swaggerExtension.documentation.serialOverrides.getOverrides().map { it.toConfigInput() },
            discriminator = swaggerExtension.documentation.polymorphicDiscriminator
        )

        val subpluginOptions = listOf(
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
                key = "format",
                value = swaggerExtension.pluginOptions.format
            ),
            SubpluginOption(
                key = "filePath",
                value = openApiOutputFile.path
            ),
            SubpluginOption(
                key = "initialConfig",
                value = Base64.encode(Json.encodeToString<ConfigInput>(initialConfig).toByteArray())
            )
        )
        return project.provider { subpluginOptions }
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

    private fun getOpenApiOutputFile(
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
}
