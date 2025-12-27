@file:Suppress("MatchingDeclarationName")
package io.github.tabilzad.ktor
import org.jetbrains.kotlin.fir.types.ConeKotlinType

/**
 * Data class for holding description information extracted from annotations.
 */
internal data class KtorDescriptionBag(
    val summary: String? = null,
    val description: String? = null,
    val tags: Set<String>? = null,
    val operationId: String? = null,
    val isRequired: Boolean? = false,
    val explicitType: String? = null,
    val serializedAs: ConeKotlinType? = null,
    val format: String? = null
)

/**
 * Converts Kotlin type names to OpenAPI/Swagger type names.
 */
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

private fun String.removeSuffixIfPresent(suffix: String): String =
    if (endsWith(suffix)) dropLast(suffix.length) else this
