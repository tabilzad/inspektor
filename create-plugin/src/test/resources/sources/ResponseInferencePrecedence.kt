package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.responds
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

data class ExplicitDude(val explicit: Boolean)
data class InferredDude(val inferred: Boolean)
data class GapError(val code: Int)

@GenerateOpenApi
fun Application.precedenceModule() {
    routing {
        get("/precedence") {
            // Explicit DSL for 200 must win over the inferred 200 below.
            responds<ExplicitDude>(HttpStatusCode.OK)
            call.respond(InferredDude(true))
            // No explicit 404 -> inference fills this gap.
            call.respond(HttpStatusCode.NotFound, GapError(404))
        }
    }
}
