package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import sources.precompiled.DeprecatedExternalType

@GenerateOpenApi
fun Application.deprecatedExternalSchemaModule() {
    routing {
        route("/v1") {
            post("/external") {
                call.receive<DeprecatedExternalType>()
            }
        }
    }
}
