package io.github.tabilzad.ktor.k2.visitors

import io.github.tabilzad.ktor.*
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

    @OptIn(SealedClassInheritorsProviderInternals::class, SymbolInternals::class)
    fun collectDataTypes(parentType: ConeKotlinType?): TypeDescriptor? {
        if (parentType == null) return null

        val fqClassName = parentType.fqNameStr()
        val typeSymbol = parentType.toRegularClassSymbol(session)
        val typeDescription = parentType.findDocsDescriptionOnType(session)
        val baseType = parentType.toBaseType(fqClassName)

        return when {
            typeDescription?.explicitType != null -> collectExplicitType(fqClassName, typeDescription)
            typeDescription?.serializedAs != null -> collectDataTypes(typeDescription.serializedAs)
            parentType.isStringOrPrimitive() -> collectPrimitive(baseType, parentType)
            parentType.isMap() -> collectMap(baseType, parentType)
            parentType.isIterable() -> collectIterable(baseType, parentType)
            parentType.isEnum || typeSymbol?.isEnumClass == true -> collectEnum(baseType, typeSymbol)
            typeSymbol?.isSealed == true -> collectSealed(baseType, fqClassName, parentType, typeSymbol)
            parentType.isAny -> baseType
            parentType.isValueClass(session) -> collectValueClass(baseType, parentType, fqClassName)
            parentType.typeArguments.isEmpty() -> collectSimpleObject(baseType, fqClassName, parentType)
            else -> collectGenericObject(baseType, fqClassName, parentType, typeSymbol)
        }
    }

    private fun collectExplicitType(fqClassName: String?, typeDescription: KtorDescriptionBag): TypeDescriptor {
        return TypeDescriptor(type = null).withReferenceBy(fqClassName) {
            typeDescription.toObjectType().copy(fqName = fqClassName)
        }
    }

    private fun collectPrimitive(baseType: TypeDescriptor, parentType: ConeKotlinType): TypeDescriptor {
        return baseType.copy(type = parentType.className()?.toSwaggerType() ?: "Unknown", ref = null)
    }

    private fun collectMap(baseType: TypeDescriptor, parentType: ConeKotlinType): TypeDescriptor {
        val valueType = parentType.typeArguments.last()
        return baseType.copy(additionalProperties = collectDataTypes(valueType.type?.resolveGeneric()))
    }

    private fun collectIterable(baseType: TypeDescriptor, parentType: ConeKotlinType): TypeDescriptor {
        val arrayItemType = parentType.typeArguments.firstNotNullOfOrNull { it.type }
        return baseType.copy("array", items = collectDataTypes(arrayItemType?.resolveGeneric()))
    }

    private fun collectEnum(baseType: TypeDescriptor, typeSymbol: FirRegularClassSymbol?): TypeDescriptor {
        return baseType.copy(type = "string", enum = typeSymbol?.resolveEnumEntries())
    }

    @OptIn(SealedClassInheritorsProviderInternals::class, SymbolInternals::class)
    private fun collectSealed(
        baseType: TypeDescriptor,
        fqClassName: String?,
        parentType: ConeKotlinType,
        typeSymbol: FirRegularClassSymbol
    ): TypeDescriptor {
        return baseType.withReferenceBy(fqClassName) {
            val discriminator = typeSymbol.resolveDiscriminator(session, config)
            val inheritorClassIds = typeSymbol.fir.sealedInheritorsAttr?.getValueOrNull()

            val sealedDescriptor = TypeDescriptor(
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

            parentType.getMembers(session, config).forEach { member ->
                member.accept(this, sealedDescriptor)
            }

            inheritorClassIds?.forEach { classId ->
                val inheritorFqName = classId.asFqNameString()
                if (!classNames.names.contains(inheritorFqName)) {
                    val inheritorType = TypeDescriptor("object", fqName = inheritorFqName)
                    classNames.add(inheritorType)
                    classId.toLookupTag().toClassSymbol(session)?.fir?.accept(this, inheritorType)
                }
            }

            sealedDescriptor
        }
    }

    private fun collectValueClass(
        baseType: TypeDescriptor,
        parentType: ConeKotlinType,
        fqClassName: String?
    ): TypeDescriptor {
        return baseType.copy(
            parentType.properties(session)?.firstOrNull()?.resolvedReturnType?.className()?.toSwaggerType(),
            fqName = fqClassName
        )
    }

    private fun collectSimpleObject(
        baseType: TypeDescriptor,
        fqClassName: String?,
        parentType: ConeKotlinType
    ): TypeDescriptor {
        return baseType.withReferenceBy(fqClassName) {
            val objectDescriptor = baseType.copy("object", fqName = fqClassName)
            parentType.getMembers(session, config).forEach { member ->
                member.accept(this, objectDescriptor)
            }
            objectDescriptor
        }
    }

    private fun collectGenericObject(
        baseType: TypeDescriptor,
        fqClassName: String?,
        parentType: ConeKotlinType,
        typeSymbol: FirRegularClassSymbol?
    ): TypeDescriptor {
        val classifiers = parentType.typeArguments.joinToString(prefix = "<", postfix = ">") {
            if (it is ConeClassLikeType) {
                it.renderReadable()
            } else {
                it.type?.resolveGeneric()?.renderReadable() ?: "UNKNOWN"
            }
        }.toGenericPostFixClassifier()

        val qualifiedGenericReference = fqClassName + classifiers
        return baseType.withReferenceBy(qualifiedGenericReference) {
            val genericDescriptor = baseType.copy("object", fqName = qualifiedGenericReference)
            val resolvedGenericParams = parentType.typeArguments.zip(
                typeSymbol?.typeParameterSymbols ?: emptyList()
            ).map { (specifiedType, genericType) ->
                GenericParameter(
                    genericTypeRef = specifiedType.type?.resolveGeneric(),
                    genericName = genericType.name.asString()
                )
            }

            parentType.getMembers(session, config).forEach { member ->
                member.accept(
                    ClassDescriptorVisitorK2(
                        config, session, context,
                        classNames = classNames,
                        genericParameters = resolvedGenericParams
                    ),
                    genericDescriptor
                )
            }
            genericDescriptor
        }
    }

    @OptIn(SymbolInternals::class)
    private fun ConeKotlinType.toBaseType(fqClassName: String?): TypeDescriptor {
        val kdocs = toRegularClassSymbol(session)?.fir?.getKDocComments(config)
        val typeDescription = findDocsDescriptionOnType(session)
        return TypeDescriptor(
            type = "object",
            fqName = fqClassName,
            description = kdocs ?: typeDescription?.description ?: typeDescription?.summary,
        )
    }

    private fun TypeDescriptor.withReferenceBy(fqName: String?, computeRef: () -> TypeDescriptor): TypeDescriptor {
        if (fqName == null) return this
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

    private fun ConeKotlinType.resolveGeneric() =
        if (this is ConeTypeParameterType) genericParameters.findTypeRefOf(this) else this

    private fun List<GenericParameter>.findTypeRefOf(generic: ConeTypeParameterType) =
        find { it.genericName == generic.renderReadable() }?.genericTypeRef

    override fun visitClass(klass: FirClass, data: TypeDescriptor): TypeDescriptor {
        klass.defaultTypeOf().getMembers(session, config).forEach { it.accept(this, data) }
        return data
    }

    override fun visitElement(element: FirElement, data: TypeDescriptor) = data

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

        resolvePropertyRequirement(propertyDescription?.isRequired, propertyName, fir)
    }

    private fun TypeDescriptor.resolvePropertyRequirement(
        isRequiredFromExplicitDesc: Boolean?,
        propertyName: String,
        fir: FirProperty
    ) {
        when {
            isRequiredFromExplicitDesc == true -> addRequired(propertyName)

            isRequiredFromExplicitDesc == null
                    && config.deriveFieldRequirementFromTypeNullability
                    && !fir.isNullable() -> {
                val symbolFromCtor = fir.findSymbolFromPrimaryCtor()
                if ((symbolFromCtor != null && !symbolFromCtor.hasDefaultValue) || fir.isAbstract) {
                    addRequired(propertyName)
                }
            }
        }
    }

    private fun TypeDescriptor.addRequired(propertyName: String) {
        if (required != null) {
            required?.add(propertyName)
        } else {
            required = mutableListOf(propertyName)
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
