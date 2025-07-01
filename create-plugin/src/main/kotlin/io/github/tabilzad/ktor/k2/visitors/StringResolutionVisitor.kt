package io.github.tabilzad.ktor.k2.visitors

import io.github.tabilzad.ktor.k2.children
import io.github.tabilzad.ktor.k2.isEnum
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.EnumValueArgumentInfo
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.collectEnumEntries
import org.jetbrains.kotlin.fir.declarations.extractEnumValueArgumentInfo
import org.jetbrains.kotlin.fir.declarations.primaryConstructorIfAny
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.impl.FirResolvedArgumentList
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitor

class StringResolutionVisitor(private val session: FirSession) : FirDefaultVisitor<String, String>() {

    override fun visitElement(element: FirElement, data: String): String = data

    // string template like "$variable-string"
    override fun visitStringConcatenationCall(
        stringConcatenationCall: FirStringConcatenationCall,
        data: String
    ): String {
        return data + (stringConcatenationCall.argumentList.arguments.joinToString("") { acc ->
            acc.accept(this@StringResolutionVisitor, data)
        })
    }

    override fun visitFunctionCall(functionCall: FirFunctionCall, data: String): String {
        return data + functionCall.children.map {
            it.accept(this, data)
        }.concat()
    }

    // TODO(Look into evaluatePropertyInitializer or evaluateExpression)
    override fun visitArgumentList(argumentList: FirArgumentList, data: String): String {
        return if (argumentList is FirResolvedArgumentList) {

            data + argumentList.mapping.keys.map {
                it.accept(this, data)
            }.concat()
        } else {
            data
        }
    }

    override fun visitLiteralExpression(literalExpression: FirLiteralExpression, data: String): String =
        data + literalExpression.value.toString()

    @OptIn(SymbolInternals::class)
    override fun visitPropertyAccessExpression(
        propertyAccessExpression: FirPropertyAccessExpression,
        data: String
    ): String {
        val enumInfo: EnumValueArgumentInfo? = propertyAccessExpression.dispatchReceiver?.extractEnumValueArgumentInfo()
        val enumEntryAccessor = propertyAccessExpression.toResolvedCallableSymbol()?.name

        if (propertyAccessExpression.isEnum(session)) {
            val entries = enumInfo?.enumClassId?.toLookupTag()?.toClassSymbol(session)?.collectEnumEntries(session)

            val v = entries?.find { it.name.asString() == enumInfo.enumEntryName.asString() }
                ?.initializerObjectSymbol
                ?.primaryConstructorIfAny(session)
                ?.fir?.delegatedConstructor

            val paramName =
                v?.resolvedArgumentMapping?.values?.find { it.name.asString() == enumEntryAccessor?.asString() }
            val paramLiteral = v?.resolvedArgumentMapping?.entries?.find { it.value == paramName }?.key

            val value = paramLiteral?.accept(this, data)
            return value ?: data
        } else {
            val resolvedSymbol = propertyAccessExpression.toResolvedCallableSymbol()
            return if (resolvedSymbol != null) {
                data + resolvedSymbol.fir.accept(this, data)
            } else {
                data
            }
        }
    }

    @OptIn(SymbolInternals::class)
    override fun visitResolvedNamedReference(
        resolvedNamedReference: FirResolvedNamedReference,
        data: String
    ): String {

        val fir = resolvedNamedReference.resolvedSymbol.fir
        return if (fir is FirProperty) {
            val init = fir.initializer

            if (init != null) {
                when (init) {
                    is FirLiteralExpression -> {
                        init.accept(this, data)
                    }

                    is FirFunctionCall -> {
                        init.accept(this, data)
                    }

                    else -> {
                        init.accept(this, data)
                    }
                }
            } else {
                data
            }
        } else {
            fir.accept(this, data)
            data
        }
    }

    private fun List<String>.concat() = joinToString("") { it }
}
