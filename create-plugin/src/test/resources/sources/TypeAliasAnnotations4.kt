package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import sources.precompiled.ExternalPricedItem

@GenerateOpenApi
fun Application.responseBody() {
    routing {
        route("/v3") {
            post("/externalPricedItem") {
                call.receive<ExternalPricedItem>()
            }
        }
    }
}
