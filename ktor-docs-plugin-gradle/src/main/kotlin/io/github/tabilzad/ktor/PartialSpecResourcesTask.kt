package io.github.tabilzad.ktor

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Stages a contributor module's partial OpenAPI spec for Android packaging.
 *
 * On JVM projects the partial-spec directory is registered directly as a resources srcDir,
 * but Android's packaging pipeline ignores Kotlin source-set resources — AGP only packages
 * java resources wired through its own variant API. This task syncs the compile-task-produced
 * partial into its own declared output directory, which the plugin registers via
 * `variant.sources.resources.addGeneratedSourceDirectory(...)` so AGP merges it into the
 * variant's java resources (and therefore the AAR/APK) with correct task dependencies.
 */
@DisableCachingByDefault(because = "copies a single small file; caching costs more than re-running")
abstract class PartialSpecResourcesTask @Inject constructor(
    private val fileSystemOperations: FileSystemOperations
) : DefaultTask() {

    /** The partial-spec directory produced by the Kotlin compile task. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val partialSpecDir: ConfigurableFileCollection

    /** Staging directory AGP wires into the variant's java resources. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun sync() {
        fileSystemOperations.sync { spec ->
            spec.from(partialSpecDir)
            spec.into(outputDir)
        }
    }
}
