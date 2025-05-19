package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorResponds
import io.github.tabilzad.ktor.annotations.ResponseEntry
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import sources.requests.PrivateBodyRequest
import sources.requests.SimpleRequest

enum class EnumResponse {
    ENTRY1, ENTRY2, ENTRY3;
}

@GenerateOpenApi
fun Application.enumResponseBody() {
    routing {
        route("/v5") {
            @KtorResponds([ResponseEntry("200", EnumResponse::class)])
            get("/respondsWithEnum") {

            }
        }
    }
}
