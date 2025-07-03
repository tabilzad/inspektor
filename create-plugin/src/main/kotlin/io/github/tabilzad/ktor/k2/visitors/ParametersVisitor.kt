package io.github.tabilzad.ktor.k2.visitors

import io.github.tabilzad.ktor.k2.isEnum
import io.ktor.http.*
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirEvaluatorResult
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.impl.FirResolvedArgumentList
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitor
import org.jetbrains.kotlin.name.FqName
import kotlin.reflect.full.memberProperties

class ParametersVisitor(
    private val session: FirSession,
    private val functionIds: List<FqName>
) : FirDefaultVisitor<Unit, MutableList<String>>() {

    override fun visitElement(element: FirElement, data: MutableList<String>) {
        // no-op
    }

    override fun visitStringConcatenationCall(
        stringConcatenationCall: FirStringConcatenationCall,
        data: MutableList<String>
    ) {
        data.add(stringConcatenationCall.argumentList.arguments.flatMap { acc ->
            buildList {
                acc.accept(this@ParametersVisitor, this)
            }
        }.joinToString(""))
    }

    override fun visitFunctionCall(functionCall: FirFunctionCall, data: MutableList<String>) {
        val functionFqName =
            functionCall.dispatchReceiver?.toResolvedCallableSymbol(session)?.callableId?.asSingleFqName()

        val functionFqName2 =
            functionCall.toResolvedCallableSymbol()?.callableId?.asSingleFqName()
        if (functionIds.any { it == functionFqName || it == functionFqName2 }
        ) {
            functionCall.acceptChildren(this, data)
        } else {
            // skip
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
    // TODO(Look into evaluatePropertyInitializer instead of evaluateExpression)
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
    @Suppress("NestedBlockDepth")
    override fun visitPropertyAccessExpression(
        propertyAccessExpression: FirPropertyAccessExpression,
        data: MutableList<String>
    ) {
        val enumInfo: EnumValueArgumentInfo? = propertyAccessExpression.dispatchReceiver?.extractEnumValueArgumentInfo()
        val enumEntryAccessor = propertyAccessExpression.calleeReference.toResolvedCallableSymbol()?.name
        if (propertyAccessExpression.isEnum(session)) {

            val entries = enumInfo?.enumClassId?.toLookupTag()?.toClassSymbol(session)?.collectEnumEntries(session)
            val v = entries?.find { it.name.asString() == enumInfo.enumEntryName.asString() }
                ?.initializerObjectSymbol
                ?.primaryConstructorIfAny(session)
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
                    } else if (init == null) {
                        // if initializer is null it is likely because the value
                        // is coming from an external library like ktor itself

                        val ktorHeader =
                            HttpHeaders::class.memberProperties.find { it.name == calleeReference.name.asString() }

                        if (ktorHeader != null) {
                            data.add(ktorHeader.getter.call(HttpHeaders).toString())
                        }
                    }
                }
            }
        }
    }
}
