package io.github.tabilzad.ktor.k2.visitors

import io.github.tabilzad.ktor.*
import io.github.tabilzad.ktor.k2.ClassIds
import io.github.tabilzad.ktor.k2.findAnnotation
import io.github.tabilzad.ktor.k2.getMembers
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.toRegularClassSymbol
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitor
import org.jetbrains.kotlin.utils.mapToSetOrEmpty

internal class ResourceClassVisitor(
    private val session: FirSession,
    private val config: PluginConfiguration,
    private val pathlessEndpoint: EndPoint,
) : FirDefaultVisitor<KtorElement?, KtorElement?>() {

    override fun visitElement(element: FirElement, data: KtorElement?): KtorElement? = data

    @OptIn(SymbolInternals::class)
    override fun visitRegularClass(regularClass: FirRegularClass, data: KtorElement?): KtorElement? {

        val resourceAnnotation = regularClass.findAnnotation(ClassIds.KTOR_RESOURCE_ANNOTATION, session)
        val resourcePath = resourceAnnotation?.accept(ResourceAnnotationVisitor(session), null)

        val (parents, params) = regularClass.symbol.defaultType()
            .getMembers(session, config)
            .filterIsInstance<FirProperty>()
            .mapNotNull {
                it.returnTypeRef.toRegularClassSymbol(session)?.let { symbol ->
                    it to symbol
                }
            }.partition { (_, symbol) ->
                symbol.hasAnnotation(ClassIds.KTOR_RESOURCE_ANNOTATION, session)
            }

        pathlessEndpoint.apply {
            parameters = if (data == null) {
                // retrieve path and query params from endpoint
                params.mapParams(resourcePath)
            } else {
                // collect path params only from the whole route hierarchy
                (params.mapParams(resourcePath)
                    .filterIsInstance<PathParamSpec>().toSet() merge parameters)
            }
            if (parameters?.isEmpty() == true) {
                parameters = null
            }
        }

        val parent = parents.firstOrNull()?.let { (_, symbol) ->
            symbol.fir.accept(this, data ?: DocRoute("/"))
        }
        val next = when (parent) {
            // reached root path
            null -> pathlessEndpoint.copy(path = resourcePath)

            is DocRoute -> parent.replaceLeafAsEndpoint(pathlessEndpoint.copy(path = resourcePath))

            is EndPoint -> DocRoute(parent.path, children = mutableListOf(parent.copy(resourcePath)))
        }
        return next
    }

    private fun List<Pair<FirProperty, FirRegularClassSymbol>>.mapParams(
        resourcePath: String?
    ) = mapToSetOrEmpty { (property, _) ->
        val description = property.findDocsDescriptionOnProperty(session)
        if (property.isPathParamFrom(resourcePath ?: "")) {
            PathParamSpec(
                name = property.name.asString(),
                description = description?.description ?: description?.summary
            )
        } else {
            QueryParamSpec(
                name = property.name.asString(),
                description = description?.description ?: description?.summary,
                isRequired = description?.isRequired ?: false
            )
        }
    }
    private fun FirProperty.isPathParamFrom(path: String): Boolean {
        val pathParams = "\\{([^}]*)}".toRegex().findAll(path).toList()
        val names = pathParams.mapNotNull { it.groups[1]?.value }
        return names.contains(name.asString())
    }

    @Suppress("NestedBlockDepth")
    private fun KtorElement.replaceLeafAsEndpoint(newEndPoint: EndPoint): KtorElement {
        when (this) {
            is DocRoute -> {
                if (this.children.isEmpty()) {
                    return newEndPoint
                } else {
                    var current: KtorElement? = this
                    while (current is DocRoute && current.children.isNotEmpty()) {
                        val next = current.children.firstOrNull()
                        if (next is EndPoint) {
                            current.children.clear()
                            current.children.add(DocRoute(next.path, children = mutableListOf(newEndPoint)))
                        }
                        current = next
                    }
                }
            }

            is EndPoint -> return newEndPoint
        }
        return this
    }
}
