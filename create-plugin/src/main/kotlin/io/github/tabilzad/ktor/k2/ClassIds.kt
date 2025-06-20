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
    KOTLINX(FqName("kotlinx.serialization.SerialName"), Name.identifier("value"))
}
