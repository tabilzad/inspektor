@file:OptIn(ExperimentalCompilerApi::class)

package io.github.tabilzad.ktor

import io.github.tabilzad.ktor.k2.OpenApiIrGenerationExtension
import io.github.tabilzad.ktor.k2.SchemaDocsCollectingChecker
import io.github.tabilzad.ktor.k2.SwaggerDeclarationChecker
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

const val PLUGIN_ID = "io.github.tabilzad.inspektor"
open class KtorMetaPluginRegistrar : CompilerPluginRegistrar() {

    override val pluginId: String
        get() = PLUGIN_ID

    override val supportsK2: Boolean
        get() = true

    @OptIn(ExperimentalCompilerApi::class)
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val config = configuration.buildPluginConfiguration()

        // FIR phase: Collect routes and schemas from @GenerateOpenApi functions
        FirExtensionRegistrarAdapter.registerExtension(SwaggerCheckers(configuration))

        // IR phase: Write collected data to file and lower responds<T>() calls
        IrGenerationExtension.registerExtension(OpenApiIrGenerationExtension(configuration, config))
    }
}

class FirCheckers(session: FirSession, configuration: CompilerConfiguration) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        // Registered as a generic function checker on purpose: the dedicated named-function slot
        // was renamed between Kotlin 2.4.10 (`simpleFunctionCheckers`) and 2.4.20
        // (`namedFunctionCheckers`), and a plugin overriding the wrong name is silently never
        // invoked by the other compiler — no error, just an empty spec. `functionCheckers` exists
        // under the same name in both, so one binary works across the 2.4 line.
        override val functionCheckers: Set<FirFunctionChecker> =
            setOf(SwaggerDeclarationChecker(session, configuration))

        // Contributor-mode KDoc sidecar collection; inert outside contributor mode.
        override val regularClassCheckers: Set<FirRegularClassChecker> =
            setOf(SchemaDocsCollectingChecker(session, configuration))
    }

    companion object {
        fun getFactory(configuration: CompilerConfiguration): Factory {
            return Factory { session -> FirCheckers(session, configuration) }
        }
    }
}

class SwaggerCheckers(private val configuration: CompilerConfiguration) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +FirCheckers.getFactory(configuration)
    }
}
