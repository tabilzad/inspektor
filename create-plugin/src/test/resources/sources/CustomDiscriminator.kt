package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

sealed class Action()

data class ActionOne(val field: Int) : Action()
data class ActionTwo(val value: String) : Action()
data object ActionThree : Action()

data class RequestBody(
    val action: Action
)

@GenerateOpenApi
fun Application.moduleAbstractions() {
    routing {
        route("/v1") {

            post("/action") {
                call.receive<RequestBody>()
            }
        }
    }
}
