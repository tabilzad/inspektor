package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

sealed interface Shape {
    data class Circle(val radius: Int) : Shape
    data class Square(val side: Int) : Shape
}

@JvmInline
value class UserId(val raw: String)

data class ValueResp(val id: UserId, val name: String)

@GenerateOpenApi
fun Application.complexTypesModule() {
    routing {
        route("/types") {
            get("/sealed") {
                val shape: Shape = Shape.Circle(1)
                call.respond(shape)
            }
            get("/value") {
                call.respond(ValueResp(UserId("u1"), "n"))
            }
        }
    }
}
