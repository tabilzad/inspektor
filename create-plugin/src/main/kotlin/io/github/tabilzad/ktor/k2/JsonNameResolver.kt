package io.github.tabilzad.ktor.k2

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.getStringArgument
import org.jetbrains.kotlin.fir.declarations.primaryConstructorIfAny
import org.jetbrains.kotlin.fir.declarations.utils.isData
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.resolve.getContainingClass
import org.jetbrains.kotlin.fir.symbols.SymbolInternals

object JsonNameResolver {

    fun getCustomNameFromAnnotation(
        property: FirProperty,
        session: FirSession,
    ): String? = getCustomNameFromPropertyAnnotation(property, session)
        ?: getMoshiNameFromDataClassConstructorParamAnnotation(property, session)

    private fun getCustomNameFromPropertyAnnotation(
        property: FirProperty,
        session: FirSession,
    ): String? = property.annotations.getCustomNameFromAnnotation(session)

    private fun List<FirAnnotation>?.getCustomNameFromAnnotation(
        session: FirSession
    ): String? = this?.let { annotations ->
        SerializationFramework.entries.mapNotNull {
            annotations
                .find { annotation -> annotation.fqName(session) == it.fqName }
                ?.getStringArgument(it.identifier, session)
        }
    }?.firstOrNull()

    @OptIn(SymbolInternals::class)
    private fun getMoshiNameFromDataClassConstructorParamAnnotation(
        property: FirProperty,
        session: FirSession,
    ): String? = property.getContainingClass()
        ?.takeIf { it.isData }
        ?.primaryConstructorIfAny(session)
        ?.valueParameterSymbols
        ?.find { it.name == property.name }
        ?.annotations
        ?.getCustomNameFromAnnotation(session)
}
