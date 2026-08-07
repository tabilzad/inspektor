package io.github.tabilzad.ktor.output

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import io.github.tabilzad.ktor.OpenApiSpecParam
import io.github.tabilzad.ktor.model.Info
import io.github.tabilzad.ktor.model.SecurityScheme
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal typealias ContentSchema = Map<String, OpenApiSpec.TypeDescriptor>

internal typealias BodyContent = Map<String, ContentSchema>

/**
 * The spec model serves two serialization purposes:
 * - Jackson writes the final openapi.json/yaml output (honoring [JsonIgnore]/[JsonProperty]);
 * - kotlinx-serialization persists the model verbatim as the multi-module partial-spec IR,
 *   where [fqName] is intentionally kept because it carries schema identity across modules.
 */
@Serializable
data class OpenApiSpec(
    val openapi: String = "3.1.0",
    val info: Info?,
    val servers: List<Server>? = null,
    val paths: Map<String, Map<String, Path>>,
    val components: OpenApiComponents,
    val security: List<Map<String, List<String>>>? = null
) {

    @Serializable
    data class Server(val url: String)

    @Serializable
    data class Path(
        val summary: String? = null,
        val description: String? = null,
        val operationId: String? = null,
        val tags: List<String>? = null,
        val responses: Map<String, ResponseDetails>? = null,
        val parameters: List<Parameter>? = null,
        val requestBody: RequestBody? = null,
        val security: List<Map<String, List<String>>>? = null,
        val deprecated: Boolean? = null
    )

    @Serializable
    data class RequestBody(
        val required: Boolean,
        val content: BodyContent
    )

    interface NamedObject {
        var fqName: String?
    }

    @Serializable
    data class TypeDescriptor(
        var type: String?,
        var properties: MutableMap<String, TypeDescriptor>? = null,
        var items: TypeDescriptor? = null,
        var enum: List<String>? = null,
        @JsonIgnore
        override var fqName: String? = null,
        var description: String? = null,
        @SerialName("\$ref")
        @JsonProperty("\$ref")
        var ref: String? = null,
        var additionalProperties: TypeDescriptor? = null,
        var oneOf: List<TypeDescriptor>? = null,
        var required: MutableList<String>? = null,
        var format: String? = null,
        var discriminator: DiscriminatorDescriptor? = null
    ) : NamedObject {
        override fun equals(other: Any?): Boolean {
            return when {
                this === other -> true
                other !is TypeDescriptor -> false
                fqName == null || other.fqName == null -> false
                else -> fqName == other.fqName
            }
        }

        override fun hashCode(): Int = fqName?.hashCode() ?: System.identityHashCode(this)
    }

    @Serializable
    data class DiscriminatorDescriptor(
        val propertyName: String = "type",
        val mapping: Map<String, String>
    )

    @Serializable
    data class Parameter(
        override val name: String,
        override val `in`: String,
        override val required: Boolean = true,
        override val description: String? = null,
        val schema: TypeDescriptor,
    ) : OpenApiSpecParam

    @Serializable
    data class ResponseDetails(
        val description: String,
        val content: BodyContent?
    )

    @Serializable
    data class OpenApiComponents(
        val schemas: Map<String, TypeDescriptor>,
        val securitySchemes: Map<String, SecurityScheme>? = null
    )
}
