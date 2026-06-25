package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

data class ExtractedDude(val id: Int)

// The respond call lives in an extracted handler function; inference must follow through it.
suspend fun handleDude(call: ApplicationCall) {
    call.respond(ExtractedDude(7))
}

// Generic receiver-extension helper: the body resolves to an unsubstituted type parameter inside the
// helper, so inference must degrade gracefully (response present, no schema) rather than emit garbage.
suspend inline fun <reified T : Any> ApplicationCall.respondAs(body: T) {
    respond(body)
}

@GenerateOpenApi
fun Application.extractedFnModule() {
    routing {
        get("/extracted") {
            handleDude(call)
        }
        get("/generic") {
            call.respondAs(ExtractedDude(9))
        }
    }
}
