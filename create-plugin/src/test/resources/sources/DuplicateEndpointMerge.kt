package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.responds
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

data class PingOk(val ok: Boolean)
data class PingError(val message: String)

// Two @GenerateOpenApi modules declare the SAME get /v1/ping with different responses.
// The merge must union both response codes rather than keep only the first.
@GenerateOpenApi
fun Application.modulePingSuccess() {
    routing {
        route("/v1") {
            get("/ping") {
                responds<PingOk>(HttpStatusCode.OK)
            }
        }
    }
}

@GenerateOpenApi
fun Application.modulePingError() {
    routing {
        route("/v1") {
            get("/ping") {
                responds<PingError>(HttpStatusCode.NotFound)
            }
        }
    }
}
