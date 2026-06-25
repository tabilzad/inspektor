package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorResponds
import io.github.tabilzad.ktor.annotations.ResponseEntry
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

data class AnnotatedDude(val annotated: Boolean)
data class InferredOnlyDude(val inferred: Boolean)
data class AnnotationGapError(val code: Int)

@GenerateOpenApi
fun Application.annotationPrecedenceModule() {
    routing {
        // @KtorResponds declares 200 explicitly; inference must not override it, but must fill 404.
        @KtorResponds([ResponseEntry("200", AnnotatedDude::class)])
        get("/annotated") {
            call.respond(InferredOnlyDude(true))
            call.respond(HttpStatusCode.NotFound, AnnotationGapError(404))
        }
    }
}
