package io.github.tabilzad.ktor.k2.visitors

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.expressions.FirCollectionLiteral
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitor

internal class StringArrayLiteralVisitor : FirDefaultVisitor<List<String>, List<String>>() {

    // if we don't override a particular visit, it will come here by default
    override fun visitElement(element: FirElement, data: List<String>): List<String> {
        return emptyList()
    }

    override fun visitLiteralExpression(literalExpression: FirLiteralExpression, data: List<String>): List<String> =
        listOf(literalExpression.value.toString())

    override fun visitCollectionLiteral(collectionLiteral: FirCollectionLiteral, data: List<String>): List<String> {
        return collectionLiteral.arguments.flatMap {
            it.accept(this, data)
        }
    }
}
