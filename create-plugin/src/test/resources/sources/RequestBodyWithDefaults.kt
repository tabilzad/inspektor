package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import sources.requests.MembersWithDefaults

@GenerateOpenApi
fun Application.payloadsWithNonNullableCollectionsWithDefaults() {
    routing {
        route("/v1"){
            post("/payloadsWithNonNullableCollectionsWithDefaults") {
                call.receive<MembersWithDefaults>()
            }
        }
    }
}
