package io.github.tabilzad.ktor.k2

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitor

internal class RespondsAnnotationVisitor : FirDefaultVisitor<List<KtorK2ResponseBag>, KtorK2ResponseBag?>() {

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
                descr = description?.accept(StringResolutionVisitor(), "") ?: "",
                status = status?.accept(StringResolutionVisitor(), "") ?: "UNKNOWN",
                type = type?.resolvedType?.typeArguments?.firstOrNull()?.type,
                isCollection = isCollection?.value as? Boolean ?: false
            )
        )
    }

    private fun LinkedHashMap<FirExpression, FirValueParameter>.findValueOfField(name: String): FirExpression? {
        return entries.find { it.value.name.asString() == name }?.key
    }

    override fun visitArrayLiteral(arrayLiteral: FirArrayLiteral, data: KtorK2ResponseBag?): List<KtorK2ResponseBag> {
        return arrayLiteral.arguments.flatMap {
            it.accept(this, data)
        }
    }

}
