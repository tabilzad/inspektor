
package io.github.tabilzad.ktor.k2.inference

import io.github.tabilzad.ktor.HttpCodeResolver
import io.github.tabilzad.ktor.k2.ClassIds
import io.github.tabilzad.ktor.k2.visitors.KtorK2ResponseBag
import org.jetbrains.kotlin.fir.BuiltinTypes
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isAny
import org.jetbrains.kotlin.fir.types.isNullableAny
import org.jetbrains.kotlin.fir.types.resolvedType

/**
 * Infers response `(status, content type, body type)` from a `call.respond*(...)` call.
 *
 * Recognition: the callee lives in `io.ktor.server.response` and its name starts with `respond`.
 * Because Ktor's `respond` is `inline fun <reified T> respond(message: T)`, the body argument
 * already carries the inferred static type in FIR — so `call.respond(localVal)` resolves without any
 * value-inlining, and `List<T>`/sealed/value-class bodies flow through the existing schema generator.
 */
internal class CallRespondInference : ResponseInferenceRule {

    override fun infer(call: FirFunctionCall, session: FirSession): List<KtorK2ResponseBag> {
        val callableId = call.toResolvedCallableSymbol()?.callableId ?: return emptyList()
        if (callableId.packageName != ClassIds.KTOR_RESPONSE_PACKAGE) return emptyList()
        val functionName = callableId.callableName.asString()
        if (!functionName.startsWith("respond")) return emptyList()

        val variant = RespondVariant.of(functionName)
        val (type, noSchema) = call.inferBody(variant)

        return listOf(
            KtorK2ResponseBag(
                descr = "",
                status = call.inferStatus(variant),
                type = type,
                isCollection = false,
                contentType = variant.contentType,
                noSchema = noSchema
            )
        )
    }

    /** First HttpStatusCode-typed argument's constant name (e.g. `Created` -> 201); else the variant default. */
    private fun FirFunctionCall.inferStatus(variant: RespondVariant): String {
        val statusArg = resolvedArgumentMapping?.keys?.firstOrNull { it.resolvedType.isHttpStatusCode() }
        val statusName = (statusArg as? FirPropertyAccessExpression)
            ?.calleeReference
            ?.let { (it as? FirResolvedNamedReference)?.name?.asString() }
        return statusName?.let { HttpCodeResolver.resolve(it) } ?: variant.defaultStatus
    }

    /** Returns the inferred body type and whether the schema should be omitted. */
    private fun FirFunctionCall.inferBody(variant: RespondVariant): Pair<ConeKotlinType?, Boolean> =
        when (variant.bodyKind) {
            // Status-only response (e.g. respondRedirect) -> Nothing renders as a body-less response.
            BodyKind.NONE -> BuiltinTypes().nothingType.coneType to false
            // Streaming/file variants: the content type is known, the schema is not.
            BodyKind.OPAQUE -> null to true
            BodyKind.MESSAGE -> {
                val bodyType = findBodyArgumentType()
                when {
                    // Erased, unresolvable, or an unsubstituted generic type parameter (e.g. a body
                    // reached through a generic extracted function) -> emit the response, omit the schema.
                    bodyType == null || bodyType.isAny || bodyType.isNullableAny ||
                        bodyType is ConeTypeParameterType -> null to true
                    else -> bodyType to false
                }
            }
        }

    /** The first value argument that is neither a status, a content type, nor a lambda — i.e. the message. */
    private fun FirFunctionCall.findBodyArgumentType(): ConeKotlinType? {
        val arguments = resolvedArgumentMapping?.keys ?: return null
        return arguments.firstOrNull { arg ->
            arg !is FirAnonymousFunctionExpression &&
                !arg.resolvedType.isHttpStatusCode() &&
                !arg.resolvedType.isContentType()
        }?.resolvedType
    }

    private fun ConeKotlinType.isHttpStatusCode(): Boolean = classId == ClassIds.KTOR_HTTP_STATUS_CODE
    private fun ConeKotlinType.isContentType(): Boolean = classId == ClassIds.KTOR_CONTENT_TYPE

    private enum class BodyKind { MESSAGE, NONE, OPAQUE }

    /**
     * Per-function-name defaults, mirroring Ktor's `getContentTypeFromFunction` / `getStatusFromFunction`.
     * Explicit `ContentType` arguments are not yet resolved (a documented limitation); content negotiation
     * defaults to `application/json` for the plain `respond`/`respondNullable` family.
     */
    private class RespondVariant(
        val contentType: String,
        val defaultStatus: String,
        val bodyKind: BodyKind
    ) {
        companion object {
            private const val JSON = "application/json"
            private const val OCTET_STREAM = "application/octet-stream"

            fun of(functionName: String): RespondVariant = when (functionName) {
                "respondText" -> RespondVariant("text/plain", "200", BodyKind.MESSAGE)
                "respondBytes" -> RespondVariant(OCTET_STREAM, "200", BodyKind.MESSAGE)
                "respondBytesWriter",
                "respondOutputStream",
                "respondSource" -> RespondVariant(OCTET_STREAM, "200", BodyKind.OPAQUE)
                "respondFile",
                "respondPath" -> RespondVariant("*/*", "200", BodyKind.OPAQUE)
                "respondHtml" -> RespondVariant("text/html", "200", BodyKind.OPAQUE)
                "respondRedirect" -> RespondVariant(JSON, "302", BodyKind.NONE)
                else -> RespondVariant(JSON, "200", BodyKind.MESSAGE)
            }
        }
    }
}
