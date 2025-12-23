@file:Suppress("NoUnusedImports")
package io.github.tabilzad.ktor.k2

import io.github.tabilzad.ktor.*
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.hasAnnotation

/**
 * Visits all declarations in the code and searches for those annotated with @GenerateOpenApi.
 * The ExpressionVisitor walks through all expressions in the function body to extract Ktor DSL
 * related data. The collected data is stored in [OpenApiSpecCollector] and later written
 * to a file during the IR phase by [OpenApiIrGenerationExtension].
 *
 * This two-phase approach (collect during FIR, write during IR) ensures:
 * - All @GenerateOpenApi functions are processed before writing
 * - No stale data from previous compilations
 * - Proper support for multiple @GenerateOpenApi functions
 */
class SwaggerDeclarationChecker(
    private val session: FirSession,
    private val configuration: CompilerConfiguration
) : FirSimpleFunctionChecker(MppCheckerKind.Common) {

    private val log = try {
        configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
    } catch (ex: Throwable) {
        null
    }
    private val config = configuration.buildPluginConfiguration()

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirSimpleFunction) {
        if (declaration.hasAnnotation(ClassIds.KTOR_GENERATE_ANNOTATION, session)) {
            val expressionsVisitor = ExpressionsVisitorK2(config, context, session, log)
            val ktorElements: List<KtorElement> = declaration.accept(expressionsVisitor, null)
            val schemas = expressionsVisitor.classNames
                .associateBy { it.fqName ?: "UNKNOWN" }

            // Collect data instead of writing directly.
            // The actual file writing happens in OpenApiIrGenerationExtension during IR phase.
            OpenApiSpecCollector.collect(
                configuration = configuration,
                key = config.filePath,
                routes = ktorElements.wrapLooseEndpoints(),
                schemas = schemas
            )
        }
    }

    private fun List<KtorElement>.wrapLooseEndpoints(): List<RouteDescriptor> = map {
        when (it) {
            is EndpointDescriptor -> RouteDescriptor("/", mutableListOf(it))
            is RouteDescriptor -> it
        }
    }
}
