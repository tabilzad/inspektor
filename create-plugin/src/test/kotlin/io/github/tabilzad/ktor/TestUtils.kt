package io.github.tabilzad.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.classgraph.ClassGraph
import io.github.tabilzad.ktor.output.OpenApiSpec
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File
import java.io.FileNotFoundException
import java.io.PrintStream
import java.nio.file.Paths
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object TestUtils {
    fun loadSourceCodeFrom(fileName: String): String =
        this.javaClass.getResource("/sources/$fileName.kt")?.readText()
            ?: throw FileNotFoundException("$fileName does not exist")

    fun loadSourceAndExpected(fileName: String): Pair<String, String> =
        loadSourceCodeFrom(fileName) to loadExpectation("$fileName-expected")

    val loadNativeAnnotations by lazy {
        Paths.get("src/main/kotlin/io/github/tabilzad/ktor/annotations/Annotations.kt").toFile()
            ?: throw FileNotFoundException("annotations don't exist")
    }

    val loadRequests by lazy {
        this.javaClass.getResource("/sources/requests/RequestDataClasses.kt")
            ?: throw FileNotFoundException("annotations don't exist")
    }

    private fun loadExpectation(fileName: String): String =
        this.javaClass.getResource("/expected/$fileName.json")?.readText()
            ?: throw FileNotFoundException("$fileName does not exist")
}

fun String.removeTrailingNewLine(): String =
    if (endsWith(System.lineSeparator())) dropLast(System.lineSeparator().length) else this

private fun classpathOf(dependency: String): File {
    val file =
        ClassGraph().classpathFiles.firstOrNull { classpath ->
            dependenciesMatch(classpath, dependency)
        }
    println("classpath: ${ClassGraph().classpathFiles}")
    Assertions.assertThat(file)
        .`as`("$dependency not found in test runtime. Check your build configuration.")
        .isNotNull
    return file!!
}

private fun sanitizeClassPathFileName(dep: String): String =
    buildList {
        var skip = false
        add(dep.first())
        dep.reduce { a, b ->
            if (a == '-' && b.isDigit()) skip = true
            if (!skip) add(b)
            b
        }
        if (skip) removeLast()
    }
        .joinToString("")
        .replace("-jvm.jar", "")
        .replace("-jvm", "")

private fun dependenciesMatch(classpath: File, dependency: String): Boolean {
    val dep = classpath.name
    val dependencyName = sanitizeClassPathFileName(dep)
    val testdep = dependency.substringBefore(":")
    return testdep == dependencyName
}

private val objectMapper by lazy { jacksonObjectMapper() }
fun File.parseSpec(): OpenApiSpec {
    return kotlin.runCatching { objectMapper.readValue<OpenApiSpec>(readText()) }.getOrElse {
        Assertions.fail("Could not parse the file ${it.message}")
    }
}

@OptIn(ExperimentalCompilerApi::class, ExperimentalEncodingApi::class)
@Suppress("LongMethod")
internal fun generateCompilerTest(
    testFile: File,
    testSubjectSource: String,
    config: PluginConfiguration = PluginConfiguration.createDefault()
) {

    val testFilePath = testFile.path
    val clp = KtorDocsCommandLineProcessor()
    val compilationData = KotlinCompilation().apply {
        compilerPluginRegistrars = listOf(KtorMetaPluginRegistrar())
        commandLineProcessors = listOf(clp)
        classpaths = deps.map { classpathOf(it) }
        sources = loadBaseSources(testSubjectSource)
        kotlincArguments = emptyList()
        jvmTarget = "1.8"
        messageOutputStream =
            object : PrintStream(System.out) {

                private val kotlincErrorRegex = Regex("^e:")

                override fun write(buf: ByteArray, off: Int, len: Int) {
                    val newLine =
                        String(buf, off, len).run { replace(kotlincErrorRegex, "error found:") }.toByteArray()

                    super.write(newLine, off, newLine.size)
                }
            }
        pluginOptions = listOf(
            com.tschuchort.compiletesting.PluginOption(
                clp.pluginId,
                KtorDocsCommandLineProcessor.isEnabled.optionName,
                "true"
            ),
            com.tschuchort.compiletesting.PluginOption(
                clp.pluginId,
                KtorDocsCommandLineProcessor.requestSchema.optionName,
                "true"
            ),
            com.tschuchort.compiletesting.PluginOption(
                clp.pluginId,
                KtorDocsCommandLineProcessor.formatOption.optionName,
                "json"
            ),
            com.tschuchort.compiletesting.PluginOption(
                clp.pluginId,
                KtorDocsCommandLineProcessor.pathOption.optionName,
                testFilePath
            ),
            com.tschuchort.compiletesting.PluginOption(
                clp.pluginId,
                KtorDocsCommandLineProcessor.hideTransientFields.optionName,
                config.hideTransients.toString()
            ),
            com.tschuchort.compiletesting.PluginOption(
                clp.pluginId,
                KtorDocsCommandLineProcessor.hidePrivateAndInternalFields.optionName,
                config.hidePrivateFields.toString()
            ),
            com.tschuchort.compiletesting.PluginOption(
                clp.pluginId,
                KtorDocsCommandLineProcessor.serverUrls.optionName,
                config.servers.joinToString("||")
            ),
            com.tschuchort.compiletesting.PluginOption(
                clp.pluginId,
                KtorDocsCommandLineProcessor.initConfig.optionName,
                Base64.encode(
                    Json.encodeToString(
                        config.initConfig.copy(
                            info = config.initConfig.info?.copy(description = "test")
                        )
                    ).toByteArray()
                )
            )
        )
    }
    compilationData.compile()
}

private fun loadBaseSources(source: String): List<SourceFile> {
    val requestsDefinitions = TestUtils.loadRequests
    return listOf(
        SourceFile.kotlin(requestsDefinitions.file, requestsDefinitions.readText().trimMargin()),
        SourceFile.kotlin("TestSubject.kt", source)
    )
}

private val deps = arrayOf(
    "ktor:2.3.12",
    "ktor-server-core:2.3.12",
    "ktor-resources:2.3.12",
    "ktor-server-resources:2.3.12",
    "ktor-utils:2.3.12",
    "ktor-server-netty:2.3.12",
    "ktor-http:2.3.12",
    "kotlinx-coroutines-core:1.10.1",
    "moshi:1.14.0",
    "kotlinx-serialization-core:2.1.0",
    "kotlinx-datetime:0.4.0",
    "annotations:0.6.7-alpha"
)
