package sources

import io.github.tabilzad.ktor.annotations.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import sources.requests.SimpleRequest

class MyGenericRespondsType<T, S>(
    val amounts: List<MyAmount<T>>,
    val currency: MyCurrency<S>
)
class MyAmount<T>(val amount: T)
class MyCurrency<T>(val curr: T)

@GenerateOpenApi
fun Application.respondsDescriptors() {
    routing {
        route("/v1") {
            post("/postBodyRequestSimple") {
                responds<MyGenericRespondsType<String, Int>>(HttpStatusCode.OK)
                respondsNothing(HttpStatusCode.NoContent)
               // call.receive<SimpleRequest>()
            }
        }
    }
}
