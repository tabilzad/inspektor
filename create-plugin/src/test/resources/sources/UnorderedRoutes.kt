package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.*
import io.ktor.server.routing.*

// Routes/methods are declared out of alphabetical order on purpose; the generated spec must
// emit them sorted so output is deterministic regardless of FIR visitation order.
@GenerateOpenApi
fun Application.moduleUnorderedRoutes() {
    routing {
        route("/v1") {
            route("/zzz") {
                post { }
                delete { }
                get { }
            }
            get("/aaa") { }
        }
    }
}
