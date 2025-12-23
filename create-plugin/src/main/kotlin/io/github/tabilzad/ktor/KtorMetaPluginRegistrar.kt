@file:OptIn(ExperimentalCompilerApi::class)

package io.github.tabilzad.ktor

import io.github.tabilzad.ktor.k2.OpenApiIrGenerationExtension
import io.github.tabilzad.ktor.k2.SwaggerDeclarationChecker
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker
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
        // these could probably be ExpressionCheckers instead of Declaration
        override val simpleFunctionCheckers: Set<FirSimpleFunctionChecker> =
            setOf(SwaggerDeclarationChecker(session, configuration))
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
