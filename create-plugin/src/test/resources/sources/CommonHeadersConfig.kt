package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.*
import io.ktor.server.routing.*

@GenerateOpenApi
fun Application.commonHeadersTest() {
    routing {
        route("/v4") {
            get("/a") {
                println("a")
            }
            post("/b") {
                println("b")
            }
            get("/c") {
                /** Tenant documented at the endpoint. */
                val tenant = call.request.headers["X-Tenant-Id"]
                println(tenant)
            }
        }
    }
}
