package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

data class InferDude(val id: Int, val name: String)
data class InferError(val message: String)

@GenerateOpenApi
fun Application.inferenceModule() {
    routing {
        route("/api") {
            get("/single") {
                call.respond(InferDude(1, "a"))
            }
            post("/created") {
                call.respond(HttpStatusCode.Created, InferDude(2, "b"))
            }
            get("/branches") {
                if (System.currentTimeMillis() > 0) {
                    call.respond(InferDude(3, "c"))
                } else {
                    call.respond(HttpStatusCode.NotFound, InferError("nope"))
                }
            }
            get("/text") {
                call.respondText("hello")
            }
            get("/redirect") {
                call.respondRedirect("/elsewhere")
            }
            get("/erased") {
                val anything: Any = InferDude(4, "d")
                call.respond(anything)
            }
            get("/list") {
                call.respond(listOf(InferDude(5, "e")))
            }
            get("/bytes") {
                call.respondBytes("hi".toByteArray())
            }
        }
    }
}
