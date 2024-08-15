package io.github.tabilzad.ktor

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.*
import java.io.File
import java.lang.IllegalArgumentException

const val PLUGIN_ID = "io.github.tabilzad.ktor-docs-plugin-gradle"

open class KtorMetaPlugin : KotlinCompilerPluginSupportPlugin {

    override fun getCompilerPluginId() = PLUGIN_ID
    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(
            groupId = "io.github.tabilzad",
            artifactId = "ktor-docs-plugin",
            version = ktorDocsVersion
        )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        return kotlinCompilation.target.project.plugins.hasPlugin(KtorMetaPlugin::class.java)
    }

    override fun apply(target: Project) {
        target.extensions.create("swagger", KtorDocsExtension::class.java).apply {
            documentation = target.extensions.create("documentation", DocumentationOptions::class.java)
            pluginOptions = target.extensions.create("pluginOptions", PluginOptions::class.java)
        }
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val swaggerExtension = project.extensions.findByType(KtorDocsExtension::class.java) ?: KtorDocsExtension()

        kotlinCompilation.dependencies {
            compileOnly("io.github.tabilzad:ktor-docs-plugin:$ktorDocsVersion")
        }

        val openApiOutputFile = with(swaggerExtension.pluginOptions) {
            getOpenApiOutputFile(
                filePath = filePath,
                saveInBuild = saveInBuild,
                buildPath = kotlinCompilation.output.resourcesDir.absolutePath, // TODO: This is build/processedResources/release, do we want /build instead? kotlinCompilation.target.project.buildDir.absolutePath
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

        val subpluginOptions = listOf(
            SubpluginOption(
                key = "enabled",
                value = swaggerExtension.pluginOptions.enabled.toString()
            ),
            SubpluginOption(
                key = "title",
                value = swaggerExtension.documentation.docsTitle
            ), SubpluginOption(
                key = "description",
                value = swaggerExtension.documentation.docsDescription
            ),
            SubpluginOption(
                key = "version",
                value = swaggerExtension.documentation.docsVersion
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
                key = "format",
                value = swaggerExtension.pluginOptions.format
            ),
            SubpluginOption(
                key = "filePath",
                value = openApiOutputFile.path
            )
        )

        return project.provider { subpluginOptions }
    }

    private fun getOpenApiOutputFile(
        filePath: String?,
        saveInBuild: Boolean,
        buildPath: String,
        modulePath: String,
        format: String,
    ): File {
        val directoryPath =  when {
            filePath != null -> filePath
            saveInBuild -> "$buildPath/openapi"
            else -> "${getProjectResourcesDirectory(modulePath)}/openapi"
        }
        val directory = createOutputDirectory(directoryPath)
        return File(directory, "openapi.$format")
    }

    private fun createOutputDirectory(path: String): File {
        return File(path).apply {
            if (!exists() && !mkdirs()) {
                throw IllegalArgumentException("Invalid output directory $path")
            }
        }
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
}
