package io.github.tabilzad.ktor

import io.github.tabilzad.ktor.output.OpenApiSpec

interface OpenApiSpecParam {
    val name: String
    @Suppress("VariableNaming")
    val `in`: String
    val required: Boolean
    val description: String?
}

internal interface ParamSpec {
    val name: String
    val description: String?
}

internal data class PathParamSpec(
    override val name: String,
    override val description: String? = null,
) : ParamSpec

internal data class QueryParamSpec(
    override val name: String,
    override val description: String? = null,
    val isRequired: Boolean = false
) : ParamSpec

internal data class HeaderParamSpec(
    override val name: String,
    override val description: String? = null,
    val isRequired: Boolean = false
) : ParamSpec

internal data class KtorRouteSpec(
    val path: String,
    val parameters: List<ParamSpec>?,
    val method: String,
    val body: OpenApiSpec.TypeDescriptor,
    val summary: String?,
    val description: String?,
    val operationId: String?,
    val tags: Set<String>?,
    val deprecation: DeprecationInfo?,
    val responses: Map<String, OpenApiSpec.ResponseDetails>?
)

/**
 * Presence marks the element as deprecated in the generated spec. OpenAPI's `deprecated` is a
 * plain boolean, so [message] (from `@Deprecated(message = ...)`) is folded into the
 * description as a "Deprecated: ..." note instead.
 */
data class DeprecationInfo(val message: String? = null)

/**
 * Combines deprecation inherited from an enclosing scope (receiver) with a more specific one
 * (argument): deprecated when either side is, and the more specific message wins when both
 * carry one.
 */
infix fun DeprecationInfo?.overriddenBy(specific: DeprecationInfo?): DeprecationInfo? = when {
    this == null -> specific
    specific == null -> this
    else -> DeprecationInfo(specific.message ?: message)
}

/** Appends the deprecation note to a description, or becomes the description when there is none. */
internal fun String?.withDeprecationNote(deprecation: DeprecationInfo?): String? {
    val message = deprecation?.message ?: return this
    val note = "Deprecated: $message"
    return if (isNullOrBlank()) note else "$this\n\n$note"
}

sealed class KtorElement {
    abstract var path: String?
    abstract var tags: Set<String>?
    abstract var deprecation: DeprecationInfo?
    abstract fun newInstance(tags: Set<String>?): KtorElement
}

enum class ExpType(val labels: List<String>) {
    ROUTE(listOf("route")),
    METHOD(listOf("get", "post", "put", "patch", "delete")),
    RECEIVE(listOf("receive"))
}

internal data class EndpointDescriptor(
    override var path: String?,
    val method: String = "",
    var body: OpenApiSpec.TypeDescriptor? = null,
    var parameters: Set<ParamSpec>? = null,
    var description: String? = null,
    var operationId: String? = null,
    var summary: String? = null,
    override var tags: Set<String>? = null,
    var responses: Map<String, OpenApiSpec.ResponseDetails>? = null,
    override var deprecation: DeprecationInfo? = null
) : KtorElement() {
    override fun newInstance(tags: Set<String>?): EndpointDescriptor {
        return copy(tags = tags)
    }
}

internal data class RouteDescriptor(
    override var path: String? = "/",
    val children: MutableList<KtorElement> = mutableListOf(),
    override var tags: Set<String>? = null,
    override var deprecation: DeprecationInfo? = null,
    /** Headers declared via `@KtorHeaders` on this route; propagated to all child endpoints. */
    var headers: Set<HeaderParamSpec>? = null
) : KtorElement() {
    override fun newInstance(tags: Set<String>?): RouteDescriptor {
        return copy(tags = tags)
    }
}

object ContentTypes {
    const val APPLICATION_JSON = "application/json"
    const val TEXT_PLAIN = "text/plain"
}
