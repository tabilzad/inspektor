package io.github.tabilzad.ktor.annotations

import kotlin.reflect.KClass

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.EXPRESSION)
annotation class GenerateOpenApi

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.EXPRESSION)
annotation class IgnoreOpenApi

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.EXPRESSION)
annotation class Tag(val tags: Array<String> = [])

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.EXPRESSION)
annotation class KtorResponds(
    val mapping: Array<ResponseEntry>
)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.EXPRESSION)
annotation class ResponseEntry(
    val status: String,
    val type: KClass<*>,
    val isCollection: Boolean = false,
    val description: String = "",
    val contentType: String = "application/json"
)

/**
 * Declares header parameters for endpoints whose code never reads the header explicitly —
 * e.g. headers consumed by middleware, interceptors, or shared controller plumbing that
 * automatic inference cannot see.
 *
 * Placement determines scope:
 * - on an endpoint expression (`get`, `post`, …) — applies to that operation only;
 * - on a `route(...)` expression — applies to every endpoint nested under that route;
 * - on an annotated route module function (e.g. alongside `@GenerateOpenApi` on
 *   `fun Route.adminApi()`) — applies to every endpoint the module defines.
 *
 * Endpoint-level declarations take precedence over route/module-level ones, which take
 * precedence over `commonHeaders` declared in the Gradle configuration.
 *
 * Example usage:
 * ```
 * @KtorHeaders([HeaderParam("X-Tenant-Id", "Tenant the request is scoped to", required = true)])
 * route("/admin") {
 *     get("/users") { /* ... */ }
 * }
 * ```
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.EXPRESSION)
annotation class KtorHeaders(
    val headers: Array<HeaderParam>
)

/**
 * A single header parameter entry used inside [KtorHeaders].
 *
 * @property name The header name as it appears on the wire (e.g. "X-Request-Id").
 * @property description A description added to the parameter in the generated spec.
 * @property required Whether the header is required. Defaults to false.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.EXPRESSION)
annotation class HeaderParam(
    val name: String,
    val description: String = "",
    val required: Boolean = false,
)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.EXPRESSION)
annotation class KtorDescription(
    val summary: String = "",
    val description: String = "",
    val operationId: String = "",
    val tags: Array<String> = []
)

/**
 * Annotation to describe fields in generated OpenAPI schemas.
 *
 * @property description A description that will be added to 'description' property of a corresponding schema field in OpenAPI.
 * @property type An optional explicit field type in OpenAPI. Can be used to override the automatic field type definition.
 *      Note: Automatic schema generation will NOT run for this field if the explicitType is not empty.
 * @property format An optional format of the data type (e.g., "date-time", "int32").
 * @property serializedAs Not currently implemented
 *
 * Example usage:
 * ```
 * @KtorSchema(description = "my user response", type="number")
 * data class DollarAmount(
 *     val value: Int
 * )
 * ```
 */

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS, AnnotationTarget.TYPE)
annotation class KtorSchema(
    val description: String = "",
    val type: String = "",
    val format: String = "",
    val serializedAs: KClass<*> = Nothing::class,
)

/**
 * Annotation to describe fields in generated OpenAPI schemas.
 *
 * @property description A description that will be added to 'description' property of a corresponding schema field in OpenAPI.
 * @property required Optional parameter that indicates whether the field is required. Overrides the automatic detection based on field nullability.
 * @property type An optional explicit field type in OpenAPI. Can be used to override the automatic field type definition.
 *      Note: Automatic schema generation will NOT run for this field if the explicitType is not empty.
 * @property format An optional format of the data type (e.g., "date-time", "int32").
 *
 * Example usage:
 * ```
 * data class UserResponse(
 *     @KtorField("Example field description")
 *     val id: String,
 *     val name: String,
 *     @KtorField(
 *         description = "Example registration date",
 *         type = "string",
 *         format = "iso8601"
 *     )
 *     val registrationDate: Instant
 * )
 * ```
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class KtorField(
    val description: String = "",
    val type: String = "",
    val format: String = "",
    val pattern: String = "",
    val required: Boolean = false,
)

@Deprecated(
    message = "Please use @KtorSchema for classes and KtorField for class properties",
    replaceWith = ReplaceWith("KtorField")
)
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.CLASS)
annotation class KtorFieldDescription(
    val summary: String = "",
    val description: String = "",
    val required: Boolean = false,
    val explicitType: String = "",
    val format: String = ""
)
