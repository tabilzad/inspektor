// NoUnusedImports: CheckerContext/DiagnosticReporter are referenced only from the
// context() parameter clause, which the import-usage analysis does not see.
@file:Suppress("NoUnusedImports")

package io.github.tabilzad.ktor.k2

import io.github.tabilzad.ktor.OpenApiSpecCollector
import io.github.tabilzad.ktor.buildPluginConfiguration
import io.github.tabilzad.ktor.getKDocComments
import io.github.tabilzad.ktor.output.SchemaDocs
import io.github.tabilzad.ktor.parseKDoc
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass

/**
 * Contributor-mode sidecar collector: records class and property KDocs for every non-local
 * class in the module as lightweight [SchemaDocs] (no type resolution), so an aggregator can
 * later enrich schemas it derived from this module's compiled binaries — where KDocs no longer
 * exist. This is what lets modules that only define data classes (no Ktor routes) still
 * contribute their documentation to the merged spec.
 *
 * Inert outside contributor mode and when KDoc extraction is disabled.
 */
class SchemaDocsCollectingChecker(
    private val session: FirSession,
    private val configuration: CompilerConfiguration
) : FirRegularClassChecker(MppCheckerKind.Common) {

    private val config = configuration.buildPluginConfiguration()

    @OptIn(DirectDeclarationsAccess::class)
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirRegularClass) {
        if (!config.isContributor || !config.useKDocsForDescriptions) return
        if (declaration.symbol.classId.isLocal) return

        val parsedClassDoc = parseKDoc(declaration.getKDocComments(config))

        val propertyDocs = declaration.declarations
            .filterIsInstance<FirProperty>()
            .mapNotNull { property ->
                val doc = property.getKDocComments(config)
                    ?: parsedClassDoc.propertyDocs[property.name.asString()]
                    ?: return@mapNotNull null
                // Keyed by the SERIALIZED property name so enrichment matches schema keys
                // (which honor @SerialName / moshi @Json) without needing FIR at merge time.
                val serializedName = JsonNameResolver.getCustomNameFromAnnotation(property, session)
                    ?: property.name.asString()
                serializedName to doc
            }
            .toMap()

        if (parsedClassDoc.text == null && propertyDocs.isEmpty()) return

        OpenApiSpecCollector.collectSchemaDocs(
            configuration = configuration,
            key = config.filePath,
            fqName = declaration.symbol.classId.asFqNameString(),
            docs = SchemaDocs(classDoc = parsedClassDoc.text, propertyDocs = propertyDocs)
        )
    }
}
