package io.github.tabilzad.ktor.k2.visitors

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitor

internal data class KtorK2ResponseBag(
    val descr: String,
    val status: String,
    val type: ConeKotlinType?,
    val isCollection: Boolean = false
)

internal class RespondsAnnotationVisitor(private val session: FirSession) : FirDefaultVisitor<List<KtorK2ResponseBag>, KtorK2ResponseBag?>() {

    // if we don't override a particular visit, it will come here by default
    override fun visitElement(element: FirElement, data: KtorK2ResponseBag?): List<KtorK2ResponseBag> {
        return emptyList()
    }

    override fun visitFunctionCall(functionCall: FirFunctionCall, data: KtorK2ResponseBag?): List<KtorK2ResponseBag> {

        val status = functionCall.resolvedArgumentMapping?.findValueOfField("status") as? FirLiteralExpression
        val type = functionCall.resolvedArgumentMapping?.findValueOfField("type") as? FirGetClassCall
        val isCollection = functionCall.resolvedArgumentMapping?.findValueOfField("isCollection") as? FirLiteralExpression
        val description =
            functionCall.resolvedArgumentMapping?.findValueOfField("description") as? FirLiteralExpression

        return listOf(
            KtorK2ResponseBag(
                descr = description?.accept(StringResolutionVisitor(session), "") ?: "",
                status = status?.accept(StringResolutionVisitor(session), "") ?: "UNKNOWN",
                type = type?.resolvedType?.typeArguments?.firstOrNull()?.type,
                isCollection = isCollection?.value as? Boolean ?: false
            )
        )
    }

    private fun LinkedHashMap<FirExpression, FirValueParameter>.findValueOfField(name: String): FirExpression? {
        return entries.find { it.value.name.asString() == name }?.key
    }

    override fun visitCollectionLiteral(collectionLiteral: FirCollectionLiteral, data: KtorK2ResponseBag?): List<KtorK2ResponseBag> {
        return collectionLiteral.arguments.flatMap {
            it.accept(this, data)
        }
    }
}
