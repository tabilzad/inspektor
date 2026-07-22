package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.HeaderParam
import io.github.tabilzad.ktor.annotations.KtorHeaders
import io.ktor.server.application.*
import io.ktor.server.routing.*

@GenerateOpenApi
@KtorHeaders([HeaderParam("X-Module-Header", "Required by every endpoint in this module.", required = true)])
fun Application.declaredHeadersTest() {
    routing {
        route("/v3") {
            @KtorHeaders([HeaderParam("X-Admin-Header", "Required by admin endpoints.")])
            route("/admin") {
                get("/users") {
                    println("users")
                }
                delete("/users") {
                    println("delete")
                }
            }

            @KtorHeaders(
                [
                    HeaderParam("X-Endpoint-Header", "Only on this endpoint.", required = true),
                    HeaderParam("X-Endpoint-Header2")
                ]
            )
            get("/single") {
                println("single")
            }

            get("/merge") {
                // inferred access of a header that is also declared at the module level
                val moduleHeader = call.request.headers["X-Module-Header"]
                println(moduleHeader)
            }
        }
    }
}
