package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

sealed class Shape {
    data class Circle(val radius: Int) : Shape()

    // Square explicitly models a field named like the default discriminator ("type").
    // The generator must reconcile it to the pinned value rather than duplicate it.
    data class Square(val type: String, val side: Int) : Shape()
}

data class ShapeRequest(
    val shape: Shape
)

@GenerateOpenApi
fun Application.moduleShape() {
    routing {
        route("/v1") {
            post("/shape") {
                call.receive<ShapeRequest>()
            }
        }
    }
}
