package io.github.tabilzad.ktor.output

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import io.github.tabilzad.ktor.ContentType
import io.github.tabilzad.ktor.OpenApiSpecParam
import io.github.tabilzad.ktor.model.Info
import io.github.tabilzad.ktor.model.SecurityScheme

internal typealias ContentSchema = Map<String, OpenApiSpec.SchemaType>

internal typealias BodyContent = Map<ContentType, ContentSchema>

data class OpenApiSpec(
    val openapi: String = "3.1.0",
    val info: Info?,
    val servers: List<Server>? = null,
    val paths: Map<String, Map<String, Path>>,
    val components: OpenApiComponents,
    val security: List<Map<String, List<String>>>? = null
) {

    data class Server(val url: String)

    data class Path(
        val summary: String? = null,
        val description: String? = null,
        val operationId: String? = null,
        val tags: List<String>? = null,
        val responses: Map<String, ResponseDetails>? = null,
        val parameters: List<Parameter>? = null,
        val requestBody: RequestBody? = null,
        val security: List<Map<String, List<String>>>? = null
    )

    data class RequestBody(
        val required: Boolean,
        val content: BodyContent
    )

    interface NamedObject {
        var fqName: String?
    }

    data class TypeDescriptor(
        var type: String?,
        var properties: MutableMap<String, TypeDescriptor>? = null,
        var items: TypeDescriptor? = null,
        var enum: List<String>? = null,
        @JsonIgnore
        override var fqName: String? = null,
        var description: String? = null,
        @JsonProperty("\$ref")
        var ref: String? = null,
        var additionalProperties: TypeDescriptor? = null,
        var oneOf: List<SchemaRef>? = null,
        var required: MutableList<String>? = null,
        var format: String? = null
    ) : NamedObject {
        override fun equals(other: Any?): Boolean {
            return when {
                this === other -> true
                other !is TypeDescriptor -> false
                other.fqName == fqName -> true
                else -> false
            }
        }

        override fun hashCode() = fqName.hashCode()
    }

    data class Parameter(
        override val name: String,
        override val `in`: String,
        override val required: Boolean = true,
        override val description: String? = null,
        val schema: SchemaType,
    ) : OpenApiSpecParam

    data class SchemaRef(
        @Suppress("ConstructorParameterNaming")
        val `$ref`: String? = null
    )

    data class SchemaType(
        val type: String? = null,
        val items: SchemaRef? = null,
        val description: String? = null,
        @Suppress("ConstructorParameterNaming")
        val `$ref`: String? = null,
    )

    data class ResponseDetails(
        val description: String,
        val content: BodyContent?
    )

    data class OpenApiComponents(
        val schemas: Map<String, TypeDescriptor>,
        val securitySchemes: Map<String, SecurityScheme>? = null
    )
}
