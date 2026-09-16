package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

data class Zone(val id: String)

/** Travels as an ISO-8601 string; see the serialOverrides of the test configuration. */
data class Timestamp(val epochSeconds: Long, val nanosecondsOfSecond: Int, val zone: Zone)

data class Payload(val note: String)

data class Event(val at: Timestamp, val payload: Payload)

@GenerateOpenApi
fun Application.events() {
    routing {
        route("/v3") {
            post("/events") {
                call.receive<Event>()
            }
        }
    }
}
