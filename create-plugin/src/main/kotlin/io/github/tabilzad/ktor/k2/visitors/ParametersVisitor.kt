package io.github.tabilzad.ktor.k2.visitors

import io.github.tabilzad.ktor.PluginConfiguration
import io.github.tabilzad.ktor.getKDocComments
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

/**
 * A parameter name extracted from a request parameter access expression, together with a
 * description resolved from the KDoc of the referenced name constant (when the whole key is a
 * single documented property reference).
 */
data class ParamMeta(
    val name: String,
    val description: String? = null
)

internal class ParametersVisitor(
    private val session: FirSession,
    private val functionIds: List<FqName>,
    private val config: PluginConfiguration? = null,
    private val implicitHeaderAccessors: Map<FqName, String> = emptyMap()
) : FirDefaultVisitor<Unit, MutableList<ParamMeta>>() {

    override fun visitElement(element: FirElement, data: MutableList<ParamMeta>) {
        // no-op
    }

    override fun visitStringConcatenationCall(
        stringConcatenationCall: FirStringConcatenationCall,
        data: MutableList<ParamMeta>
    ) {
        // A concatenated/templated key is only partially described by any single piece,
        // so the joined name deliberately carries no description.
        data.add(
            ParamMeta(
                name = stringConcatenationCall.argumentList.arguments.flatMap { acc ->
                    buildList {
                        acc.accept(this@ParametersVisitor, this)
                    }
                }.joinToString("") { it.name }
            )
        )
    }

    override fun visitFunctionCall(functionCall: FirFunctionCall, data: MutableList<ParamMeta>) {
        val functionFqName =
            functionCall.dispatchReceiver?.toResolvedCallableSymbol(session)?.callableId?.asSingleFqName()

        val functionFqName2 =
            functionCall.toResolvedCallableSymbol()?.callableId?.asSingleFqName()

        // Typed accessors like `call.request.userAgent()` take no argument; the header name is
        // implied by the function itself.
        val implicitHeader = implicitHeaderAccessors[functionFqName2] ?: implicitHeaderAccessors[functionFqName]
        if (implicitHeader != null) {
            data.add(ParamMeta(implicitHeader))
            return
        }

        if (functionIds.any { it == functionFqName || it == functionFqName2 }
        ) {
            functionCall.acceptChildren(this, data)
        } else {
            // skip
        }
    }

    override fun visitLiteralExpression(literalExpression: FirLiteralExpression, data: MutableList<ParamMeta>) {
        val element = literalExpression.value
        element?.let { data.add(ParamMeta(it.toString())) }
    }

    @OptIn(SymbolInternals::class)
    override fun visitResolvedNamedReference(
        resolvedNamedReference: FirResolvedNamedReference,
        data: MutableList<ParamMeta>
    ) {
        val fir = resolvedNamedReference.resolvedSymbol.fir
        if (fir is FirProperty) {
            val init = fir.initializer

            if (init is FirLiteralExpression) {
                init.value?.let { data.add(ParamMeta(it.toString(), fir.kDocDescription())) }
            }
        }
    }

    @OptIn(PrivateConstantEvaluatorAPI::class)
    // TODO(Look into evaluatePropertyInitializer instead of evaluateExpression)
    override fun visitArgumentList(argumentList: FirArgumentList, data: MutableList<ParamMeta>) {
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
        data: MutableList<ParamMeta>
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
                data.add(ParamMeta(queryParam.toString()))
            }
        } else {
            val calleeReference = propertyAccessExpression.calleeReference
            if (calleeReference is FirResolvedNamedReference) {
                val fir = calleeReference.resolvedSymbol.fir
                if (fir is FirProperty) {
                    val init = fir.initializer

                    if (init is FirLiteralExpression) {
                        init.value?.let { data.add(ParamMeta(it.toString(), fir.kDocDescription())) }
                    } else if (init == null) {
                        // if initializer is null it is likely because the value
                        // is coming from an external library like ktor itself.
                        // Guarded: a reflective failure must degrade to "no parameter",
                        // not crash the consumer's compilation.
                        val ktorHeader = runCatching {
                            HttpHeaders::class.memberProperties
                                .find { it.name == calleeReference.name.asString() }
                                ?.getter?.call(HttpHeaders)?.toString()
                        }.getOrNull()

                        if (ktorHeader != null) {
                            data.add(ParamMeta(ktorHeader))
                        }
                    }
                }
            }
        }
    }

    private fun FirProperty.kDocDescription(): String? =
        config?.let { getKDocComments(it) }?.ifBlank { null }
}
