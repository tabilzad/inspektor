package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*


data class NestedGenerics<T, S>(
    val types: List<List<List<T>>>,
    val map: Map<String, Set<S>>,
    val genericValueMap: Map<String, S>
)

@GenerateOpenApi
fun Application.moduleAbstractions4() {
    routing {
        route("/v1") {
            post("/nestedGenerics") {
                call.receive<NestedGenerics<String, Int>>()
            }
        }
    }
}
