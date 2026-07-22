package io.github.tabilzad.ktor.k2.visitors

import io.github.tabilzad.ktor.HeaderParamSpec
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitor

/**
 * Parses the `headers = [HeaderParam(...)]` argument of a `@KtorHeaders` annotation into
 * [HeaderParamSpec]s. Mirrors [RespondsAnnotationVisitor].
 */
internal class HeadersAnnotationVisitor(
    private val session: FirSession
) : FirDefaultVisitor<List<HeaderParamSpec>, Nothing?>() {

    override fun visitElement(element: FirElement, data: Nothing?): List<HeaderParamSpec> = emptyList()

    override fun visitFunctionCall(functionCall: FirFunctionCall, data: Nothing?): List<HeaderParamSpec> {
        val name = functionCall.resolvedArgumentMapping?.findValueOfField("name") as? FirLiteralExpression
        val description = functionCall.resolvedArgumentMapping?.findValueOfField("description") as? FirLiteralExpression
        val required = functionCall.resolvedArgumentMapping?.findValueOfField("required") as? FirLiteralExpression

        val headerName = name?.accept(StringResolutionVisitor(session), "")?.ifBlank { null }
            ?: return emptyList()

        return listOf(
            HeaderParamSpec(
                name = headerName,
                description = description?.accept(StringResolutionVisitor(session), "")?.ifBlank { null },
                isRequired = required?.value as? Boolean ?: false
            )
        )
    }

    private fun LinkedHashMap<FirExpression, FirValueParameter>.findValueOfField(name: String): FirExpression? {
        return entries.find { it.value.name.asString() == name }?.key
    }

    override fun visitCollectionLiteral(
        collectionLiteral: FirCollectionLiteral,
        data: Nothing?
    ): List<HeaderParamSpec> {
        return collectionLiteral.arguments.flatMap {
            it.accept(this, data)
        }
    }
}
