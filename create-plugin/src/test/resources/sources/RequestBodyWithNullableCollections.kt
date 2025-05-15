package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import sources.requests.NullableCollectionsWithDefaults
import sources.requests.NullableCollections

@GenerateOpenApi
fun Application.payloadsWithNullableCollections() {
    routing {
        route("/v1"){
            post("/nullableCollections") {
                call.receive<NullableCollections>()
            }

            post("/nullableCollectionsWithDefaults") {
                call.receive<NullableCollectionsWithDefaults>()
            }
        }
    }
}
