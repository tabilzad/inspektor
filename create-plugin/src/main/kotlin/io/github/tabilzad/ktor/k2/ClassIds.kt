package io.github.tabilzad.ktor.k2

import io.github.tabilzad.ktor.annotations.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

object ClassIds {

    val KTOR_ROUTING_PACKAGE = FqName("io.ktor.server.routing")
    val KTOR_RECEIVE = FqName("io.ktor.server.request.receive")
    val KTOR_RESPONDS_NO_OP = FqName("io.github.tabilzad.ktor.annotations.responds")
    val KTOR_RESPONDS_NOTHING_NO_OP = FqName("io.github.tabilzad.ktor.annotations.respondsNothing")

    // Automatic response inference: the `respond*` family lives in this package.
    val KTOR_RESPONSE_PACKAGE = FqName("io.ktor.server.response")
    val KTOR_HTTP_STATUS_CODE = ClassId(FqName("io.ktor.http"), FqName("HttpStatusCode"), false)
    val KTOR_CONTENT_TYPE = ClassId(FqName("io.ktor.http"), FqName("ContentType"), false)

    val KTOR_APPLICATION = ClassId(FqName("io.ktor.server.application"), FqName("Application"), false)

    val KTOR_ROUTE = ClassId(KTOR_ROUTING_PACKAGE, FqName("Route"), false)

    val KTOR_ROUTING = ClassId(KTOR_ROUTING_PACKAGE, FqName("Routing"), false)
    val KTOR_RESOURCES = FqName("io.ktor.server.resources")

    val KTOR_QUERY_PARAM = FqName("io.ktor.server.request.ApplicationRequest.queryParameters")
    val KTOR_3_QUERY_PARAM = FqName("io.ktor.server.routing.RoutingRequest.queryParameters")
    val KTOR_RAW_QUERY_PARAM = FqName("io.ktor.server.request.ApplicationRequest.rawQueryParameters")
    val KTOR_3_RAW_QUERY_PARAM = FqName("io.ktor.server.routing.RoutingRequest.rawQueryParameters")

    val KTOR_HEADER_PARAM = FqName("io.ktor.server.request.ApplicationRequest.headers")
    val KTOR_3_HEADER_PARAM = FqName("io.ktor.server.routing.RoutingRequest.headers")
    val KTOR_HEADER_ACCESSOR = FqName("io.ktor.server.request.header")
    val KTOR_3_HEADER_ACCESSOR = FqName("io.ktor.server.routing.RoutingRequest.header")

    /**
     * Ktor's typed request accessors that read a well-known header without taking the header
     * name as an argument. `accept()`, `authorization()`, `contentType()` and `contentCharset()`
     * are intentionally absent: OpenAPI mandates that header parameters named `Accept`,
     * `Content-Type` or `Authorization` be ignored, so no parameter is generated for them.
     */
    val KTOR_IMPLICIT_HEADER_ACCESSORS: Map<FqName, String> = mapOf(
        FqName("io.ktor.server.request.userAgent") to "User-Agent",
        FqName("io.ktor.server.request.acceptLanguage") to "Accept-Language",
        FqName("io.ktor.server.request.acceptCharset") to "Accept-Charset",
        FqName("io.ktor.server.request.acceptEncoding") to "Accept-Encoding",
        FqName("io.ktor.server.request.cacheControl") to "Cache-Control",
        FqName("io.ktor.server.request.ranges") to "Range",
    )

    val KTOR_TAGS_ANNOTATION =
        ClassId(FqName("io.github.tabilzad.ktor.annotations"), Tag::class.asSimpleFqName(), false)
    val KTOR_GENERATE_ANNOTATION =
        ClassId(FqName("io.github.tabilzad.ktor.annotations"), GenerateOpenApi::class.asSimpleFqName(), false)
    val KTOR_RESOURCE_ANNOTATION = ClassId(FqName("io.ktor.resources"), FqName("Resource"), false)

    val KTOR_DSL_ANNOTATION = ClassId(FqName("io.ktor.util"), FqName("KtorDsl"), false)
    val TRANSIENT_ANNOTATION = ClassId(FqName("kotlin.jvm"), Transient::class.asSimpleFqName(), false)

    val TRANSIENT_ANNOTATION_FQ = Transient::class.asQualifiedFqName()
    val KTOR_FIELD_DESCRIPTION = KtorFieldDescription::class.asQualifiedFqName()
    val KTOR_DESCRIPTION = KtorDescription::class.asQualifiedFqName()
    val KTOR_SCHEMA = KtorSchema::class.asQualifiedFqName()
    val KTOR_FIELD = KtorField::class.asQualifiedFqName()
    val KTOR_RESPONDS = KtorResponds::class.asQualifiedFqName()
    val DEPRECATED = Deprecated::class.asQualifiedFqName()
}

private fun KClass<*>.asSimpleFqName(): FqName = FqName(
    this::simpleName.get()
        ?: throw IllegalArgumentException("Could not find simple class name for ${this.jvmName}")
)

private fun KClass<*>.asQualifiedFqName(): FqName = FqName(
    this::qualifiedName.get()
        ?: throw IllegalArgumentException("Could not find qualified class name for ${this.jvmName}")
)

enum class SerializationFramework(val fqName: FqName, val identifier: Name) {
    MOSHI(FqName("com.squareup.moshi.Json"), Name.identifier("name")),
    KOTLINX_SERIAL_NAME(FqName("kotlinx.serialization.SerialName"), Name.identifier("value")),
    KOTLINX_JSON_DISCRIMINATOR(FqName("kotlinx.serialization.json.JsonClassDiscriminator"), Name.identifier("discriminator"))
}
