package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonClassDiscriminator
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

@JsonClassDiscriminator("__typename")
sealed class Action()

@SerialName("ACTION_ONE")
data class ActionOne(val field: Int) : Action()
@SerialName("ACTION_TWO")
data class ActionTwo(val value: String) : Action()
@SerialName("ACTION_THREE")
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
