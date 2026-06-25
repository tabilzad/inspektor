package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

data class WrappedResp(val ok: Boolean)

// A custom domain DSL / higher-order wrapper around the response block. Inference must descend into
// the nested lambda and still resolve the respond inside it.
suspend fun <T> auditing(block: suspend () -> T): T = block()

@GenerateOpenApi
fun Application.nestedDslModule() {
    routing {
        get("/wrapped") {
            auditing {
                call.respond(WrappedResp(true))
            }
        }
    }
}
