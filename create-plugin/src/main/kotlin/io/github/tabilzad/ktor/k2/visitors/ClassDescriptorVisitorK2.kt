package io.github.tabilzad.ktor.k2.visitors

import io.github.tabilzad.ktor.*
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.annotations.KtorFieldDescription
import io.github.tabilzad.ktor.k1.visitors.KtorDescriptionBag
import io.github.tabilzad.ktor.k1.visitors.toSwaggerType
import io.github.tabilzad.ktor.k2.*
import io.github.tabilzad.ktor.k2.ClassIds.KTOR_FIELD_DESCRIPTION
import io.github.tabilzad.ktor.k2.JsonNameResolver.getCustomNameFromAnnotation
import io.github.tabilzad.ktor.output.OpenApiSpec
import io.github.tabilzad.ktor.output.OpenApiSpec.ObjectType
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.isValueClass
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.SealedClassInheritorsProviderInternals
import org.jetbrains.kotlin.fir.declarations.sealedInheritorsAttr
import org.jetbrains.kotlin.fir.declarations.utils.correspondingValueParameterFromPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.declarations.utils.isEnumClass
import org.jetbrains.kotlin.fir.declarations.utils.isSealed
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.util.getValueOrNull

data class GenericParameter(
    val genericName: String,
    val genericTypeRef: ConeKotlinType?
)

internal class ClassDescriptorVisitorK2(
    private val config: PluginConfiguration,
    private val session: FirSession,
    private val context: CheckerContext,
    private val genericParameters: List<GenericParameter> = emptyList(),
    val classNames: MutableList<ObjectType> = mutableListOf(),
) : FirDefaultVisitor<ObjectType, ObjectType>() {

    override fun visitProperty(property: FirProperty, data: ObjectType): ObjectType {
        val coneTypeOrNull = property.returnTypeRef.coneTypeOrNull!!
        val type = if (coneTypeOrNull is ConeTypeParameterType && genericParameters.isNotEmpty()) {
            genericParameters.find { it.genericName == coneTypeOrNull.renderReadable() }?.genericTypeRef!!
        } else {
            coneTypeOrNull
        }
        val resolvedDescription = property.findDocsDescription(session)
        return if (resolvedDescription != null && resolvedDescription.explicitType?.isNotEmpty() == true) {
            data.apply { addProperty(property, null, resolvedDescription) }
        } else {
            data.apply { addProperty(property, type.collectDataTypes(), resolvedDescription) }
        }
    }

    @OptIn(SealedClassInheritorsProviderInternals::class, SymbolInternals::class)
    @Suppress("LongMethod", "NestedBlockDepth", "CyclomaticComplexMethod")
    private fun ConeKotlinType.collectDataTypes(): ObjectType? {
        val fqClassName = fqNameStr()
        val typeSymbol = toRegularClassSymbol(session)

        return when {
            isStringOrPrimitive() -> {
                ObjectType(type = className()?.toSwaggerType() ?: "Unknown")
            }

            isMap() -> {
                // map keys are assumed to be strings,
                // so getting the last type which is the value type
                val valueType = typeArguments.last()
                valueType.type?.let { valueClassType ->
                    ObjectType("object", mutableMapOf()).apply {
                        additionalProperties = valueClassType.collectDataTypes()
                    }
                }
            }

            isIterable() -> {
                val arrayItemType = typeArguments.firstNotNullOfOrNull { it.type }
                // lists only take a single generic type
                ObjectType("array", items = arrayItemType?.collectDataTypes())
            }

            isEnum || typeSymbol?.isEnumClass == true -> {
                val enumValues = typeSymbol?.resolveEnumEntries()
                ObjectType(type = "string", enum = enumValues)
            }

            typeSymbol?.isSealed == true -> {

                if (!classNames.names.contains(fqClassName)) {
                    val inheritorClassIds = typeSymbol.fir.sealedInheritorsAttr?.getValueOrNull()
                    val internal = ObjectType(
                        "object",
                        fqName = fqClassName,
                        oneOf = inheritorClassIds?.map { OpenApiSpec.SchemaRef("#/components/schemas/${it.asFqNameString()}") }
                    )
                    classNames.add(internal)
                    getMembers(session, config).forEach { nestedDescr ->
                        nestedDescr.accept(this@ClassDescriptorVisitorK2, internal)
                    }

                    inheritorClassIds?.forEach { it: ClassId ->

                        val inheritorType = ObjectType(
                            "object",
                            fqName = it.asFqNameString(),
                        )
                        classNames.add(inheritorType)
                        val fir: FirClass? = it.toLookupTag().toClassSymbol(session)?.fir
                        fir?.accept(this@ClassDescriptorVisitorK2, inheritorType)
                    }
                }

                ObjectType(
                    type = null,
                    fqName = fqClassName,
                    ref = "#/components/schemas/$fqClassName"
                )
            }

            isAny -> {
                ObjectType("object")
            }

            isValueClass(session) -> {
                ObjectType(
                    properties(session)?.firstOrNull()?.resolvedReturnType?.className()?.toSwaggerType(),
                    fqName = fqClassName
                )
            }

            else -> {

                if (!classNames.names.contains(fqClassName)) {
                    val internal = ObjectType(
                        "object",
                        fqName = fqClassName
                    )
                    classNames.add(internal)

                    if (typeArguments.isEmpty()) {
                        getMembers(session, config).forEach { nestedDescr ->
                            nestedDescr.accept(this@ClassDescriptorVisitorK2, internal)
                        }
                    } else {
                        getMembers(session, config)
                            .map { nestedDescr ->
                                nestedDescr.accept(
                                    ClassDescriptorVisitorK2(
                                        config, session, context,
                                        classNames = classNames,
                                        genericParameters = typeArguments.zip(
                                            typeSymbol?.typeParameterSymbols ?: emptyList()
                                        ).map { (specifiedType, genericType) ->
                                            GenericParameter(
                                                genericTypeRef = specifiedType.type,
                                                genericName = genericType.name.asString()
                                            )
                                        }
                                    ),
                                    internal
                                )
                            }
                    }
                }

                ObjectType(
                    type = null,
                    fqName = fqClassName,
                    // description = docsDescription,
                    ref = "#/components/schemas/$fqClassName"
                )
            }
        }
    }

    override fun visitClass(klass: FirClass, data: ObjectType): ObjectType {
        klass.defaultType().getMembers(session, config).forEach { it.accept(this, data) }
        return data
    }

    override fun visitElement(element: FirElement, data: ObjectType) = data

    @Suppress("CyclomaticComplexMethod")
    private fun ObjectType.addProperty(
        fir: FirProperty,
        objectType: ObjectType?,
        resolvedDescription: KtorDescriptionBag?
    ) {
        val kdoc = fir.getKDocComments(config)
        val docsDescription = resolvedDescription.let { it?.summary ?: it?.description }
        val name = fir.findName()
        val spec = objectType ?: ObjectType("object")
        if (properties == null) {
            properties = mutableMapOf(name to spec)
        } else {
            properties?.put(name, spec)
        }

        spec.description = docsDescription ?: kdoc

        val isRequiredFromExplicitDesc = resolvedDescription?.isRequired
        if (isRequiredFromExplicitDesc != null && isRequiredFromExplicitDesc) {
            required?.add(name) ?: run {
                required = mutableListOf(name)
            }
        } else if ((isRequiredFromExplicitDesc == null && !fir.returnTypeRef.coneType.isMarkedNullable)
            && config.deriveFieldRequirementFromTypeNullability
            && (fir.shouldBeRequiredByInitializer() || fir.isAbstract)
        ) {
            required?.add(name) ?: run {
                required = mutableListOf(name)
            }
        }
        if (resolvedDescription?.format != null) {
            spec.format = resolvedDescription.format
        }
        if (resolvedDescription?.explicitType != null) {
            spec.type = resolvedDescription.explicitType
        }
    }

    private fun FirProperty.shouldBeRequiredByInitializer(): Boolean {
        val derivedParamFromPrimaryCtor = correspondingValueParameterFromPrimaryConstructor
        return if (derivedParamFromPrimaryCtor != null) {
            !derivedParamFromPrimaryCtor.hasDefaultValue
        } else {
            false
        }
    }

    private fun FirProperty.findName(): String {
        return getCustomNameFromAnnotation(this, session) ?: name.asString()
    }
}

internal fun FirProperty.findDocsDescription(session: FirSession): KtorDescriptionBag? {
    val docsAnnotation =
        findAnnotationNamed(KtorDescription::class.simpleName)
            ?: findAnnotationNamed(KtorFieldDescription::class.simpleName)
            ?: return null

    val dataBag = docsAnnotation.extractDescription(session)
    return dataBag.copy(
        isRequired = dataBag.isRequired ?: (!returnTypeRef.coneType.isMarkedNullable)
    )
}

internal fun ConeKotlinType.findDocsDescription(session: FirSession): KtorDescriptionBag? {
    val docsAnnotation = toRegularClassSymbol(session)?.annotations
        ?.find { it.fqName(session) == KTOR_FIELD_DESCRIPTION }

    if (docsAnnotation == null) return null
    val dataBag = docsAnnotation.extractDescription(session)
    return dataBag.copy(
        isRequired = dataBag.isRequired ?: (!isMarkedNullable)
    )
}
