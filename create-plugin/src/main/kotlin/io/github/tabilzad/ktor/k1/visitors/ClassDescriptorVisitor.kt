package io.github.tabilzad.ktor.k1.visitors

import io.github.tabilzad.ktor.PluginConfiguration
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.forEachVariable
import io.github.tabilzad.ktor.names
import io.github.tabilzad.ktor.output.OpenApiSpec.TypeDescriptor
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.DeclarationDescriptorVisitorEmptyBodies
import org.jetbrains.kotlin.js.descriptorUtils.getKotlinTypeFqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.lazy.ForceResolveUtil
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeProjection
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext.isArrayOrNullableArray
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection
import org.jetbrains.kotlin.types.typeUtil.isAny
import org.jetbrains.kotlin.types.typeUtil.isEnum
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlin.util.removeSuffixIfPresent

internal class ClassDescriptorVisitor(val config: PluginConfiguration, val context: BindingContext) :
    DeclarationDescriptorVisitorEmptyBodies<TypeDescriptor, TypeDescriptor>() {

    val classNames = mutableListOf<TypeDescriptor>()

    @Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth")
    override fun visitPropertyDescriptor(
        descriptor: PropertyDescriptor,
        parent: TypeDescriptor
    ): TypeDescriptor {

        ForceResolveUtil.forceResolveAllContents(descriptor.annotations)
        val type = descriptor.type
        val propertyName = descriptor.resolvePropertyName()
        val docsDescription = descriptor.findDocsDescription()

        val result = when {
            KotlinBuiltIns.isPrimitiveType(type.asTypeProjection().type) || KotlinBuiltIns.isString(type.asTypeProjection().type) -> {
                if (parent.type == "object") {
                    parent.properties?.put(
                        propertyName,
                        TypeDescriptor(
                            type.toString().toSwaggerType(),
                            description = docsDescription
                        )
                    )
                }
                if (parent.type == "array") {

                    val thisPrimitiveObj = TypeDescriptor(
                        type.toString().toSwaggerType(),
                        description = docsDescription
                    )
                    parent.items = thisPrimitiveObj
                }
                parent
            }

            else -> {
                val fqClassName = type.getKotlinTypeFqName(true)

                when {
                    type.isIterable() || type.isArrayOrNullableArray() -> {

                        parent.properties?.put(
                            propertyName,
                            listObjectType(type, docsDescription)
                        )
                        parent
                    }

                    type.isEnum() -> {

                        val enumValues = type.memberScope.resolveEnumValues()

                        parent.properties?.put(
                            propertyName, TypeDescriptor(
                                "string",
                                enum = enumValues,
                                description = docsDescription
                            )
                        )
                        parent
                    }

                    type.isMap() -> {

                        val valueType = type.arguments.last()

                        fun TypeProjection.createMapDefinition(): TypeDescriptor {
                            val classDescriptor = DescriptorUtils.getClassDescriptorForType(this.type)
                            val valueClassType = classDescriptor.defaultType
                            val acc = TypeDescriptor("object", mutableMapOf())

                            when {
                                KotlinBuiltIns.isPrimitiveType(valueClassType) || KotlinBuiltIns.isString(
                                    valueClassType
                                ) -> {
                                    acc.additionalProperties =
                                        TypeDescriptor(classDescriptor.name.asString().toSwaggerType())
                                }

                                valueClassType.isIterable() || valueClassType.isArrayOrNullableArray() -> {
                                    acc.type = "object"
                                    acc.additionalProperties = listObjectType(
                                        type = this@createMapDefinition.type,
                                        docsDescription = docsDescription,
                                    )
                                }

                                valueClassType.isEnum() -> {
                                    acc.type = "object"
                                    acc.additionalProperties = TypeDescriptor("string",
                                        enum = valueClassType.memberScope.getClassifierNames()?.map { it.asString() }
                                            ?.minus("Companion")
                                    )
                                }

                                KotlinBuiltIns.isAny(valueClassType) -> {
                                    acc.type = "object"
                                }

                                else -> {
                                    val gName = valueClassType.getKotlinTypeFqName(false)
                                    if (!classNames.names.contains(gName)) {
                                        val q = TypeDescriptor(
                                            "object",
                                            mutableMapOf(),
                                            fqName = gName,
                                            description = docsDescription
                                        )
                                        classNames.add(q)

                                        valueClassType.memberScope
                                            .forEachVariable(config) { nestedDescr: DeclarationDescriptor ->
                                                nestedDescr.accept(this@ClassDescriptorVisitor, q)
                                            }
                                    }

                                    acc.additionalProperties = TypeDescriptor(
                                        type = null,
                                        ref = "#/components/schemas/$gName"
                                    )
                                }
                            }

                            return acc
                        }

                        val item = valueType.createMapDefinition()
                        parent.properties?.put(
                            propertyName,
                            item
                        )
                        parent
                    }

                    type.isAny() -> {
                        parent
                    }

                    else -> {

                        if (!classNames.names.contains(fqClassName)) {
                            val internal = TypeDescriptor(
                                "object",
                                mutableMapOf(),
                                fqName = fqClassName
                            )
                            classNames.add(internal)
                            type.memberScope
                                .forEachVariable(config) { nestedDescr ->
                                    nestedDescr.accept(this, internal)
                                }
                        }
                        if (parent.properties != null) {
                            parent.properties?.put(
                                propertyName,
                                TypeDescriptor(
                                    type = null,
                                    fqName = fqClassName,
                                    description = docsDescription,
                                    ref = "#/components/schemas/$fqClassName"
                                )
                            )
                        } else {
                            parent.properties = mutableMapOf(
                                propertyName to
                                        TypeDescriptor(
                                            type = null,
                                            fqName = fqClassName,
                                            description = docsDescription,
                                            ref = "#/components/schemas/$fqClassName"
                                        )
                            )
                        }
                        parent
                    }
                }
            }
        }
        return result
    }

    private fun listObjectType(
        type: KotlinType,
        docsDescription: String?
    ): TypeDescriptor {
        val types = type.unfoldNestedParameters().reversed().map {
            DescriptorUtils.getClassDescriptorForType(it.type)
        }

        return types.fold(
            TypeDescriptor(
                "object",
                mutableMapOf(),
                description = docsDescription
            )
        ) { acc: TypeDescriptor, d: ClassDescriptor ->
            val classType = d.defaultType
            var t = acc
            while (t.items != null) {
                t = t.items!!
            }
            if (KotlinBuiltIns.isPrimitiveType(classType) || KotlinBuiltIns.isString(classType)) {
                t.type = d.name.asString().toSwaggerType()
                t.properties = null
            } else if (classType.isIterable() || classType.isArrayOrNullableArray()) {
                t.type = "array"
                t.items = TypeDescriptor(null, mutableMapOf())
            } else if (classType.isEnum()) {
                t.type = "string"
                t.enum = classType.memberScope.resolveEnumValues()
            } else {
                t.type = null
                val jetTypeFqName = classType.getKotlinTypeFqName(false)

                if (!classNames.names.contains(jetTypeFqName)) {

                    val q = TypeDescriptor(
                        "object",
                        properties = mutableMapOf(),
                        fqName = jetTypeFqName,
                        description = docsDescription
                    )
                    classNames.add(q)
                    classType.memberScope
                        .forEachVariable(config) { nestedDescr: DeclarationDescriptor ->
                            nestedDescr.accept(this, q)
                        }
                }
                t.apply {
                    ref = "#/components/schemas/$jetTypeFqName"
                    properties = null
                }
            }
            acc
        }
    }

    private fun PropertyDescriptor.resolvePropertyName(): String {
        val moshiJsonName = getMoshiNameFromBackingField() ?: getMoshiNameFromDataClassConstructorParameter()
        return moshiJsonName ?: name.toString()
    }

    private fun PropertyDescriptor.getMoshiNameFromBackingField(): String? {
        return backingField?.annotations?.getMoshiJsonName()
    }

    private fun Annotations.getMoshiJsonName(): String? {
        return findAnnotation(MOSHI_JSON_ANNOTATION_FQ_NAME)
            ?.allValueArguments
            ?.get(MOSHI_JSON_ANNOTATION_NAME_ARGUMENT_IDENTIFIER)
            ?.value?.toString()
    }

    private fun PropertyDescriptor.getMoshiNameFromDataClassConstructorParameter(): String? {
        val containingClass = this.containingDeclaration as? ClassDescriptor ?: return null

        if (!containingClass.isData) return null

        return containingClass.unsubstitutedPrimaryConstructor
            ?.valueParameters
            ?.find { it.name == this.name }
            ?.annotations
            ?.getMoshiJsonName()
    }

    companion object {
        private val MOSHI_JSON_ANNOTATION_FQ_NAME = FqName("com.squareup.moshi.Json")
        private val MOSHI_JSON_ANNOTATION_NAME_ARGUMENT_IDENTIFIER: Name = Name.identifier("name")
    }
}

private fun PropertyDescriptor.findDocsDescription(): String? {
    val text =
        backingField
            ?.annotations
            ?.find { it.fqName?.asString()?.contains(KtorDescription::class.simpleName!!) == true }
            ?.let { annotationDescriptor ->
                val summary = annotationDescriptor.allValueArguments.get(Name.identifier("summary"))?.value as? String
                val description =
                    annotationDescriptor.allValueArguments.get(Name.identifier("description"))?.value as? String
                summary ?: description
            }
    return text
}

fun KotlinType.isIterable(): Boolean {
    return supertypes().map { it.getKotlinTypeFqName(false) }
        .contains(DefaultBuiltIns.Instance.iterableType.getKotlinTypeFqName(false))
}

fun KotlinType.isMap(): Boolean {
    val jetTypeFqName = getKotlinTypeFqName(false)
    return listOf(
        DefaultBuiltIns.Instance.map.defaultType.getKotlinTypeFqName(false),
        DefaultBuiltIns.Instance.mutableMap.defaultType.getKotlinTypeFqName(false)
    ).any { it == jetTypeFqName }
}

fun KotlinType.unfoldNestedParameters(params: List<TypeProjection> = this.arguments): List<TypeProjection> {
    return arguments.flatMap {
        it.type.unfoldNestedParameters()
    }.plus(asTypeProjection())
}

fun MemberScope.resolveEnumValues(): List<String> {
    return getContributedDescriptors(DescriptorKindFilter.VALUES)
        .map { it.name.asString() }
        .filterNot {
            listOf(
                "name",
                "ordinal",
                "clone",
                "compareTo",
                "describeConstable",
                "equals",
                "finalize",
                "getDeclaringClass",
                "hashCode",
                "toString"
            ).contains(it)
        }
}

fun String.toSwaggerType(): String {
    return when (val type = this.lowercase().removeSuffixIfPresent("?")) {
        "int", "kotlin/int" -> "integer"
        "double", "kotlin/double" -> "number"
        "float", "kotlin/float" -> "number"
        "long", "kotlin/long" -> "integer"
        "string", "kotlin/string" -> "string"
        "boolean", "kotlin/boolean" -> "boolean"
        else -> type
    }
}
