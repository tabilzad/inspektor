package io.github.tabilzad.ktor.k2

import io.github.tabilzad.ktor.PluginConfiguration
import io.github.tabilzad.ktor.byFeatureFlag
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.fullyExpandedClassId
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.isEnumClass
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirEnumEntrySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.load.kotlin.internalName
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.StandardClassIds

internal class FirstLevelNodeCollector(private val elements: MutableSet<FirElement>) : FirVisitorVoid() {
    override fun visitElement(element: FirElement) {
        elements.add(element)
    }
}

internal class AllNestedNodeCollector(private val elements: MutableSet<FirElement>) : FirVisitorVoid() {
    override fun visitElement(element: FirElement) {
        elements.add(element)
        element.acceptChildren(this)
    }
}

class ExpressionTree(
    val node: FirElement,
    val children: MutableList<ExpressionTree> = mutableListOf()
) {

    fun <T> walk(data: T, onVisit: (FirElement, T) -> List<T>): List<T> {
        onVisit(node, data)
        return children.flatMap {
            walk(data, onVisit)
        }
    }

    fun countAll(): Int {
        return children.fold(children.count()) { acc, expressionTree ->
            acc + expressionTree.countAll()
        }
    }

    fun render(): String {
        return buildString {
            append("Node: ${node.render()}")
            children.forEach {
                append("Child: " + it.render())
            }
        }
    }
}

internal class HierarchyResolver(private val root: ExpressionTree) : FirVisitorVoid() {

    override fun visitElement(element: FirElement) {
        val nextRoot = ExpressionTree(element)
        root.children.add(nextRoot)
        element.acceptChildren(HierarchyResolver(nextRoot))
    }
}

val FirElement.buildHierarchy: ExpressionTree
    get() {
        val root = ExpressionTree(this)
        acceptChildren(HierarchyResolver(root))
        return root
    }

internal val FirElement.children: MutableSet<FirElement>
    get() {
        val elements = mutableSetOf<FirElement>()
        acceptChildren(FirstLevelNodeCollector(elements))
        return elements
    }

internal val FirElement.allChildren: MutableSet<FirElement>
    get() {
        val elements = mutableSetOf<FirElement>()
        acceptChildren(AllNestedNodeCollector(elements))
        return elements
    }

@OptIn(SymbolInternals::class)
internal val FirTypeRef.getKotlinTypeFqName
    get(): String? {
        // Ensure the type is resolved
        val coneType = coneTypeSafe<ConeKotlinType>() ?: return null
        val classId = coneType.classId
        return classId?.internalName
    }

fun FirFunction.hasAnnotationNamed(session: FirSession, name: String): Boolean =
    annotations.any { it.fqName(session)?.shortName()?.asString() == name }

fun FirStatement.findAnnotationNamed(name: FqName) = annotations.firstOrNull {
    it.annotationTypeRef.coneType.classId?.asSingleFqName() == name
}

fun FirProperty.findPropAnnotationNamed(name: FqName?): FirAnnotation? {
    if (name == null) return null
    return backingField?.annotations?.firstOrNull {
        it.annotationTypeRef.coneType.classId?.asSingleFqName() == name
    }
}

fun FirFunction.getAnnotationNamed(name: String): FirAnnotation? = annotations.firstOrNull {
    it.annotationTypeRef.coneType.fqNameStr()?.contains(name) == true
}

fun FirStatement.findAnnotation(classId: ClassId, session: FirSession): FirAnnotation? {
    return annotations.firstOrNull {
        it.annotationTypeRef.coneType.fullyExpandedClassId(session) == classId
    }
}

internal fun ConeKotlinType.properties(session: FirSession) =
    toRegularClassSymbol(session)?.declarationSymbols?.filterIsInstance<FirPropertySymbol>()

@OptIn(SymbolInternals::class)
internal fun ConeKotlinType.getMembers(session: FirSession, config: PluginConfiguration): List<FirDeclaration> {

    val concreteDeclarations =
        (this as? ConeClassLikeType)?.lookupTag?.toClassSymbol(session)?.fir?.declarations?.filterIsInstance<FirProperty>()
            ?: emptyList()

    val abstractDeclarations = this.toRegularClassSymbol(session)?.resolvedSuperTypes?.flatMap {
        it.toRegularClassSymbol(session)?.toLookupTag()?.toClassSymbol(session)?.fir?.declarations ?: emptyList()
    }?.filterIsInstance<FirProperty>()
        ?.filter { !concreteDeclarations.map { it.name.asString() }.contains(it.name.asString()) } ?: emptyList()

    return (concreteDeclarations + abstractDeclarations).asSequence()
        .filter { !it.isLocal }
        .filter { it.getter?.body == null }
        .filter {
            it.visibility.isPublicAPI.byFeatureFlag(config.hidePrivateFields)
        }
        .filter {
            (!it.annotations.hasAnnotation(ClassIds.TRANSIENT_ANNOTATION, session)).byFeatureFlag(config.hideTransients)
        }
        .filter {
            (!(it.backingField?.annotations?.hasAnnotation(ClassIds.TRANSIENT_ANNOTATION, session) == true
                    || it.delegate?.annotations?.hasAnnotation(ClassIds.TRANSIENT_ANNOTATION, session) == true
                    || it.setter?.annotations?.hasAnnotation(ClassIds.TRANSIENT_ANNOTATION, session) == true
                    || it.getter?.annotations?.hasAnnotation(ClassIds.TRANSIENT_ANNOTATION, session) == true)
                    ).byFeatureFlag(config.hideTransients)
        }
        .toList()
}

fun ConeKotlinType.className(): String? = classId?.asString()
fun ConeKotlinType.fqNameStr(): String? = classId?.asFqNameString()
fun ConeKotlinType.fqName(): FqName? = classId?.asSingleFqName()

@Suppress("CyclomaticComplexMethod")
fun ConeKotlinType.isIterable(): Boolean {
    return isArrayTypeOrNullableArrayType
            || isArrayType
            || isArrayOrPrimitiveArray
            || isList
            || isMutableList
            || isSet
            || isMutableSet
            || isBuiltinType(StandardClassIds.List, isNullable = true)
            || isBuiltinType(StandardClassIds.MutableList, isNullable = true)
            || isBuiltinType(StandardClassIds.Set, isNullable = true)
            || isBuiltinType(StandardClassIds.MutableSet, isNullable = true)
            || isBuiltinType(StandardClassIds.Collection, isNullable = false)
            || isBuiltinType(StandardClassIds.Collection, isNullable = true)
            || isBuiltinType(StandardClassIds.Iterable, isNullable = false)
            || isBuiltinType(StandardClassIds.Iterable, isNullable = true)
            || isBuiltinType(StandardClassIds.Iterator, isNullable = true)
            || isBuiltinType(StandardClassIds.Iterator, isNullable = false)
}

fun ConeKotlinType.isMap(): Boolean {
    return isMap || isMutableMap || isBuiltinType(StandardClassIds.MutableMap, true) ||
            isBuiltinType(StandardClassIds.Map, true)
}

private fun ConeKotlinType.isBuiltinType(classId: ClassId, isNullable: Boolean?): Boolean {
    if (this !is ConeClassLikeType) return false
    return lookupTag.classId == classId && (isNullable == null || this.isMarkedNullable == isNullable)
}

fun FirRegularClassSymbol.resolveEnumEntries(): List<String> {
    return declarationSymbols.filterIsInstance<FirEnumEntrySymbol>().map { it.name.asString() }
}

fun FirRegularClassSymbol.findEnumParamValue(value: String): List<String> {
    return declarationSymbols.filterIsInstance<FirEnumEntrySymbol>().map { it.name.asString() }
}

fun FirPropertyAccessExpression.isEnum(session: FirSession): Boolean = this.dispatchReceiver
    ?.toResolvedCallableSymbol(session)
    ?.resolvedReturnType
    ?.toRegularClassSymbol(session)?.isEnumClass == true

fun <T> T?.wrapAsList() = this?.let { listOf(this) } ?: emptyList()
