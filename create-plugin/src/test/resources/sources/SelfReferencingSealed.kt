package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import kotlinx.serialization.json.JsonElement
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

data class SelfReferencingSealed(
    val json: JsonElement
)

@GenerateOpenApi
fun Application.responseBody5() {
    routing {
        route("/v3") {
            post("/sealedJsonElement") {
                call.receive<SelfReferencingSealed>()
            }
        }
    }
}
