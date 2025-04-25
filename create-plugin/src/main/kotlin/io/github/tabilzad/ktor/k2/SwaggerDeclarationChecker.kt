package io.github.tabilzad.ktor.k2

import io.github.tabilzad.ktor.DocRoute
import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.buildPluginConfiguration
import io.github.tabilzad.ktor.convertInternalToOpenSpec
import io.github.tabilzad.ktor.serializeAndWriteTo
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.hasAnnotation


/**
 * check function visits all declarations in the code and searches for those annotated with @GenerateOpenApi.
 * Then the ExpressionVisitor walks through all expressions in the function body to extract Ktor dsl related data
 * and convert it to Open API specification
 */
class SwaggerDeclarationChecker(
    private val session: FirSession,
    configuration: CompilerConfiguration
) : FirSimpleFunctionChecker(MppCheckerKind.Common) {

    private val log = configuration.messageCollector
    private val config = configuration.buildPluginConfiguration()

    override fun check(declaration: FirSimpleFunction, context: CheckerContext, reporter: DiagnosticReporter) {
        if (
            declaration.hasAnnotation(ClassIds.KTOR_DOCS_ANNOTATION, session) ||
            declaration.hasAnnotation(ClassIds.KTOR_GENERATE_ANNOTATION, session) ||
            declaration.hasAnnotation(session, GenerateOpenApi::class.simpleName!!) ||
            declaration.hasAnnotation(session, "KtorDocs")
        ) {
            val expressionsVisitor = ExpressionsVisitorK2(config, context, session, log)
            val rawRoutes = declaration.accept(expressionsVisitor, null)

            val routes: List<DocRoute> = if (rawRoutes.any { it !is DocRoute }) {
                val (routes, endpoints) = rawRoutes.partition { it is DocRoute }
                val docRoutes = routes as List<DocRoute>
                docRoutes.plus(DocRoute("/", endpoints.toMutableList()))
            } else {
                rawRoutes as List<DocRoute>
            }

            val components = expressionsVisitor.classNames
                .associateBy { it.fqName ?: "UNKNOWN" }

            convertInternalToOpenSpec(
                routes = routes,
                configuration = config,
                schemas = components
            ).serializeAndWriteTo(config)
        }


    }
}
