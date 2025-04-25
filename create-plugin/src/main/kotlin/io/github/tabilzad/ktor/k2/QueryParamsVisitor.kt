package io.github.tabilzad.ktor.k2

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirEvaluatorResult
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.primaryConstructorSymbol
import org.jetbrains.kotlin.fir.declarations.EnumValueArgumentInfo
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.collectEnumEntries
import org.jetbrains.kotlin.fir.declarations.extractEnumValueArgumentInfo
import org.jetbrains.kotlin.fir.declarations.utils.isEnumClass
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.impl.FirResolvedArgumentList
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitor

class QueryParamsVisitor(private val session: FirSession) : FirDefaultVisitor<Unit, MutableList<String>>() {

    override fun visitElement(element: FirElement, data: MutableList<String>) {
        // no-op
    }

    override fun visitStringConcatenationCall(
        stringConcatenationCall: FirStringConcatenationCall,
        data: MutableList<String>
    ) {
        val args = stringConcatenationCall.argumentList.arguments
        val l = mutableListOf<String>()
        args.forEach {
            it.accept(this, l)
        }
        data.add(l.joinToString(""))
    }


    override fun visitFunctionCall(functionCall: FirFunctionCall, data: MutableList<String>) {
        val d = functionCall.dispatchReceiver?.toResolvedCallableSymbol(session)?.callableId?.asSingleFqName()
        if (d == ClassIds.KTOR_QUERY_PARAM || d == ClassIds.KTOR_RAW_QUERY_PARAM) {
            functionCall.acceptChildren(this, data)
        } else {
            println()
        }
    }

    override fun visitLiteralExpression(literalExpression: FirLiteralExpression, data: MutableList<String>) {
        val element = literalExpression.value
        element?.let { data.add(it.toString()) }
    }

    @OptIn(SymbolInternals::class)
    override fun visitResolvedNamedReference(
        resolvedNamedReference: FirResolvedNamedReference,
        data: MutableList<String>
    ) {
        val fir = resolvedNamedReference.resolvedSymbol.fir
        if (fir is FirProperty) {
            val init = fir.initializer

            if (init is FirLiteralExpression) {
                init.accept(this, data)
            }
        }
    }

    @OptIn(PrivateConstantEvaluatorAPI::class)
    override fun visitArgumentList(argumentList: FirArgumentList, data: MutableList<String>) {

        if (argumentList is FirResolvedArgumentList) {
            val g = argumentList.mapping.keys
                .filterIsInstance<FirFunctionCall>()
                .map {
                    FirExpressionEvaluator.evaluateExpression(it, session)
                }.filterIsInstance<FirEvaluatorResult.Evaluated>().map {
                    it.result
                }.filterIsInstance<FirLiteralExpression>()

            g.forEach { it.accept(this, data) }

        }

        argumentList.acceptChildren(this, data)
    }

    @OptIn(SymbolInternals::class)
    override fun visitPropertyAccessExpression(
        propertyAccessExpression: FirPropertyAccessExpression,
        data: MutableList<String>
    ) {

        val isEnum =
            propertyAccessExpression.dispatchReceiver?.toResolvedCallableSymbol(session)?.resolvedReturnType?.toRegularClassSymbol(
                session
            )?.isEnumClass == true
        val enumInfo: EnumValueArgumentInfo? = propertyAccessExpression.dispatchReceiver?.extractEnumValueArgumentInfo()
        val enumEntryAccessor = propertyAccessExpression.calleeReference.toResolvedCallableSymbol()?.name

        if (isEnum) {

            val entries = enumInfo?.enumClassId?.toLookupTag()?.toClassSymbol(session)?.collectEnumEntries()
            val v = entries?.find { it.name.asString() == enumInfo.enumEntryName.asString() }
                ?.initializerObjectSymbol
                ?.primaryConstructorSymbol(session)
                ?.fir?.delegatedConstructor

            val paramName =
                v?.resolvedArgumentMapping?.values?.find { it.name.asString() == enumEntryAccessor?.asString() }
            val paramLiteral = v?.resolvedArgumentMapping?.entries?.find { it.value == paramName }?.key

            val queryParam = (paramLiteral as? FirLiteralExpression)?.value
            queryParam?.let {
                data.add(queryParam.toString())
            }
        } else {
            val calleeReference = propertyAccessExpression.calleeReference
            if (calleeReference is FirResolvedNamedReference) {
                val fir = calleeReference.resolvedSymbol.fir
                if (fir is FirProperty) {
                    val init = fir.initializer

                    if (init is FirLiteralExpression) {
                        init.accept(this, data)
                    }
                }
            }

        }

    }
}
