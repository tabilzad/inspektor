package io.github.tabilzad.ktor.k2

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitor

class StringResolutionVisitor : FirDefaultVisitor<String, String>() {

    override fun visitElement(element: FirElement, data: String): String = data

    override fun visitFunctionCall(functionCall: FirFunctionCall, data: String): String =
        functionCall.dispatchReceiver?.let { dr ->
            data + dr.accept(this, data)
        } ?: data

    override fun visitLiteralExpression(literalExpression: FirLiteralExpression, data: String): String =
        literalExpression.value.toString()
}
