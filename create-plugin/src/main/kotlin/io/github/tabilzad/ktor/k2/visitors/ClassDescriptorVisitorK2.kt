package io.github.tabilzad.ktor.k2.visitors

import io.github.tabilzad.ktor.*
import io.github.tabilzad.ktor.k1.visitors.KtorDescriptionBag
import io.github.tabilzad.ktor.k1.visitors.toSwaggerType
import io.github.tabilzad.ktor.k2.*
import io.github.tabilzad.ktor.k2.ClassIds.KTOR_DESCRIPTION
import io.github.tabilzad.ktor.k2.ClassIds.KTOR_FIELD
import io.github.tabilzad.ktor.k2.ClassIds.KTOR_FIELD_DESCRIPTION
import io.github.tabilzad.ktor.k2.ClassIds.KTOR_SCHEMA
import io.github.tabilzad.ktor.k2.JsonNameResolver.getCustomNameFromAnnotation
import io.github.tabilzad.ktor.output.OpenApiSpec
import io.github.tabilzad.ktor.output.OpenApiSpec.TypeDescriptor
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.isValueClass
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.correspondingValueParameterFromPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.declarations.utils.isEnumClass
import org.jetbrains.kotlin.fir.declarations.utils.isSealed
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.resolve.getContainingClass
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
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
    val classNames: MutableSet<TypeDescriptor> = mutableSetOf(),
) : FirDefaultVisitor<TypeDescriptor, TypeDescriptor>() {

    override fun visitProperty(property: FirProperty, data: TypeDescriptor): TypeDescriptor {
        val coneTypeOrNull = property.returnTypeRef.coneTypeOrNull
        val propertyDescription = property.findDocsDescriptionOnProperty(session)

        return if (propertyDescription != null && propertyDescription.explicitType?.isNotEmpty() == true) {
            data.apply { addProperty(property, propertyDescription.toObjectType(), propertyDescription) }
        } else {
            data.apply {
                addProperty(
                    property,
                    typeDescriptor = collectDataTypes(
                        propertyDescription?.serializedAs ?: coneTypeOrNull?.resolveGeneric()
                    ),
                    propertyDescription
                )
            }
        }
    }

    fun TypeDescriptor.withReferenceBy(fqName: String?, computeRef: () -> TypeDescriptor): TypeDescriptor {
        if (fqName == null) return this
        // type not allowed alongside ref
        type = null
        if (!classNames.names.contains(fqName)) {
            val element = computeRef()
            val override = config.initConfig.overrides.find { it.fqName == fqName }
            if (override != null) {
                element.apply {
                    type = override.serializedAs ?: type
                    description = override.description ?: description
                    format = override.format ?: format
                }
            }
            classNames.add(element)
        }
        return copy(type = null, ref = "#/components/schemas/$fqName")
    }

    @OptIn(SealedClassInheritorsProviderInternals::class, SymbolInternals::class)
    @Suppress("LongMethod", "NestedBlockDepth", "CyclomaticComplexMethod")
    fun collectDataTypes(parentType: ConeKotlinType?): TypeDescriptor? {

        if (parentType == null) return null
        val kdocs = parentType.toRegularClassSymbol(session)
            ?.fir
            ?.getKDocComments(config)

        val typeDescription = parentType.findDocsDescriptionOnType(session)
        val fqClassName = parentType.fqNameStr()
        val typeSymbol = parentType.toRegularClassSymbol(session)

        val baseType = TypeDescriptor(
            type = "object",
            fqName = fqClassName,
            description = kdocs ?: typeDescription?.description ?: typeDescription?.summary,
        )

        return when {

            typeDescription?.explicitType != null -> {
                TypeDescriptor(
                    type = null,
                ).withReferenceBy(fqClassName) { typeDescription.toObjectType().copy(fqName = fqClassName) }
            }

            typeDescription?.serializedAs != null -> {
                collectDataTypes(typeDescription.serializedAs)
            }

            parentType.isStringOrPrimitive() -> {
                baseType.copy(type = parentType.className()?.toSwaggerType() ?: "Unknown", ref = null)
            }

            parentType.isMap() -> {
                // map keys are assumed to be strings,
                // so getting the last type which is the value type
                val valueType = parentType.typeArguments.last()
                baseType.copy(additionalProperties = collectDataTypes(valueType.type?.resolveGeneric()))
            }

            parentType.isIterable() -> {
                val arrayItemType = parentType.typeArguments.firstNotNullOfOrNull { it.type }
                // lists only take a single generic type
                baseType.copy("array", items = collectDataTypes(arrayItemType?.resolveGeneric()))
            }

            parentType.isEnum || typeSymbol?.isEnumClass == true -> {
                val enumValues = typeSymbol?.resolveEnumEntries()
                baseType.copy(type = "string", enum = enumValues)
            }

            typeSymbol?.isSealed == true -> {

                baseType.withReferenceBy(fqClassName) {

                    val discriminator = typeSymbol.resolveDiscriminator(session, config)
                    val inheritorClassIds = typeSymbol.fir.sealedInheritorsAttr?.getValueOrNull()
                    val internal = TypeDescriptor(
                        "object",
                        fqName = fqClassName,
                        oneOf = inheritorClassIds?.map {
                            TypeDescriptor(ref = "#/components/schemas/${it.asFqNameString()}", type = null)
                        },
                        discriminator = OpenApiSpec.DiscriminatorDescriptor(
                            propertyName = discriminator,
                            mapping = inheritorClassIds?.associate {
                                it.resolveDiscriminatorValue(session) to "#/components/schemas/${it.asFqNameString()}"
                            } ?: emptyMap()
                        )
                    )
                    parentType.getMembers(session, config).forEach { nestedDescr ->
                        nestedDescr.accept(this, internal)
                    }

                    inheritorClassIds?.forEach { it: ClassId ->
                        val fqName1 = it.asFqNameString()
                        val inheritorType = TypeDescriptor(
                            "object",
                            fqName = fqName1,
                        )
                        if (!classNames.names.contains(fqName1)) {
                            classNames.add(inheritorType)
                        }
                        val fir: FirClass? = it.toLookupTag().toClassSymbol(session)?.fir
                        fir?.accept(this, inheritorType)
                    }
                    internal
                }
            }

            parentType.isAny -> baseType

            parentType.isValueClass(session) -> {
                baseType.copy(
                    parentType.properties(session)?.firstOrNull()?.resolvedReturnType?.className()?.toSwaggerType(),
                    fqName = fqClassName
                )
            }

            else -> {

                if (parentType.typeArguments.isEmpty()) {
                    baseType.withReferenceBy(fqClassName) {
                        val internal = baseType.copy(
                            "object",
                            fqName = fqClassName
                        )
                        parentType.getMembers(session, config).forEach { nestedDescr ->
                            nestedDescr.accept(this, internal)
                        }
                        internal
                    }
                } else {
                    val classifiers = parentType.typeArguments.joinToString(prefix = "<", postfix = ">") {
                        if (it is ConeClassLikeType) {
                            it.renderReadable()
                        } else {
                            it.type?.resolveGeneric()?.renderReadable() ?: "UNKNOWN"
                        }
                    }.toGenericPostFixClassifier()
                    val qualifiedGenericReference = fqClassName + classifiers
                    baseType.withReferenceBy(qualifiedGenericReference) {
                        val internal = baseType.copy(
                            "object",
                            fqName = qualifiedGenericReference
                        )
                        parentType.getMembers(session, config)
                            .map { nestedDescr ->
                                nestedDescr.accept(
                                    ClassDescriptorVisitorK2(
                                        config, session, context,
                                        classNames = classNames,
                                        genericParameters = parentType.typeArguments.zip(
                                            typeSymbol?.typeParameterSymbols ?: emptyList()
                                        ).map { (specifiedType, genericType) ->
                                            GenericParameter(
                                                genericTypeRef = specifiedType.type?.resolveGeneric(),
                                                genericName = genericType.name.asString()
                                            )
                                        }
                                    ),
                                    internal
                                )
                            }
                        internal
                    }
                }
            }
        }
    }

    private fun ConeKotlinType.resolveGeneric() =
        if (this is ConeTypeParameterType) genericParameters.findTypeRefOf(this) else this

    fun List<GenericParameter>.findTypeRefOf(generic: ConeTypeParameterType) =
        find { it.genericName == generic.renderReadable() }?.genericTypeRef

    override fun visitClass(klass: FirClass, data: TypeDescriptor): TypeDescriptor {
        klass.defaultTypeOf().getMembers(session, config).forEach { it.accept(this, data) }
        return data
    }

    override fun visitElement(element: FirElement, data: TypeDescriptor) = data

    @Suppress("CyclomaticComplexMethod")
    private fun TypeDescriptor.addProperty(
        fir: FirProperty,
        typeDescriptor: TypeDescriptor?,
        propertyDescription: KtorDescriptionBag?
    ) {
        val kdoc = fir.getKDocComments(config)
        val docsDescription = propertyDescription.let { it?.summary ?: it?.description }
        val propertyName = fir.findName()
        val spec = typeDescriptor ?: TypeDescriptor(type = "object")
        if (properties == null) {
            properties = mutableMapOf(propertyName to spec)
        } else {
            properties?.put(propertyName, spec)
        }

        spec.description = docsDescription ?: spec.description ?: kdoc

        val isRequiredFromExplicitDesc = propertyDescription?.isRequired
        resolvedPropertyRequirement(isRequiredFromExplicitDesc, propertyName, fir)
    }

    private fun TypeDescriptor.resolvedPropertyRequirement(
        isRequiredFromExplicitDesc: Boolean?,
        propertyName: String,
        fir: FirProperty
    ) {
        if (isRequiredFromExplicitDesc == true) {
            required?.add(propertyName) ?: run {
                required = mutableListOf(propertyName)
            }
        } else if (
            isRequiredFromExplicitDesc == null // not specified on annotation explicitly with true or false
            && config.deriveFieldRequirementFromTypeNullability // opted in to derive from type
            && !fir.isNullable() // not nullable
        ) {
            fir.correspondingValueParameterFromPrimaryConstructor
            val symbolFromCtor = fir.findSymbolFromPrimaryCtor()
            if (symbolFromCtor != null && !symbolFromCtor.hasDefaultValue) {
                required?.add(propertyName) ?: run {
                    required = mutableListOf(propertyName)
                }
            } else if (fir.isAbstract) {
                required?.add(propertyName) ?: run {
                    required = mutableListOf(propertyName)
                }
            }
        }
    }

    private fun FirProperty.isNullable() = returnTypeRef.coneType.isMarkedNullable
    private fun FirProperty.findSymbolFromPrimaryCtor(): FirValueParameterSymbol? {
        return getContainingClass()
            ?.constructors(session)
            ?.firstOrNull { it.isPrimary }
            ?.valueParameterSymbols
            ?.associateBy { it.name.asString() }
            ?.get(name.asString())
    }

    private fun FirProperty.findName(): String {
        return getCustomNameFromAnnotation(this, session) ?: name.asString()
    }
}

@OptIn(SymbolInternals::class)
private fun FirRegularClassSymbol.resolveDiscriminator(session: FirSession, config: PluginConfiguration): String {
    val discriminatorFq = SerializationFramework.KOTLINX_JSON_DISCRIMINATOR
    return annotations
        .find { annotation -> annotation.fqName(session) == discriminatorFq.fqName }
        ?.getStringArgument(discriminatorFq.identifier, session) ?: config.discriminator
}

@OptIn(SymbolInternals::class)
private fun ClassId.resolveDiscriminatorValue(session: FirSession): String {
    val serialNameFq = SerializationFramework.KOTLINX_SERIAL_NAME
    val symbol = toLookupTag().toClassSymbol(session)
    val explicitlyAnnotated = symbol?.annotations
        ?.find { annotation -> annotation.fqName(session) == serialNameFq.fqName }
        ?.getStringArgument(serialNameFq.identifier, session)
    return explicitlyAnnotated ?: asFqNameString()
}

internal fun FirProperty.findDocsDescriptionOnProperty(session: FirSession): KtorDescriptionBag? {
    val docsAnnotation =
        findPropAnnotationNamed(KTOR_DESCRIPTION)
            ?: findPropAnnotationNamed(KTOR_FIELD_DESCRIPTION)
            ?: findPropAnnotationNamed(KTOR_SCHEMA)
            ?: findPropAnnotationNamed(KTOR_FIELD)
            ?: return null

    val dataBag = docsAnnotation.extractDescription(session)
    return dataBag.copy(
        isRequired = dataBag.isRequired ?: (!returnTypeRef.coneType.isMarkedNullable)
    )
}

@OptIn(SymbolInternals::class)
internal fun ConeKotlinType.findDocsDescriptionOnType(session: FirSession): KtorDescriptionBag? {
    val docsAnnotation = ((toRegularClassSymbol(session)?.annotations ?: emptyList()) + typeAnnotations)
        .find {
            it.fqName(session) == KTOR_FIELD_DESCRIPTION
                    || it.fqName(session) == KTOR_SCHEMA
                    || it.fqName(session) == KTOR_FIELD
        }

    if (docsAnnotation == null) return null
    val dataBag = docsAnnotation.extractDescription(session)
    return dataBag.copy(
        isRequired = dataBag.isRequired ?: (!isMarkedNullable)
    )
}
