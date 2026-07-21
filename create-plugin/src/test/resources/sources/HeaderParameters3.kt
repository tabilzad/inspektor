package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

object ApiHeaders {
    /** Identifies the tenant the request is scoped to. */
    const val TENANT_ID = "X-Tenant-Id"

    /** Correlation id propagated across services. */
    val REQUEST_ID = "X-Request-Id"
}

object ApiQueries {
    /** Zero-based page index. */
    const val PAGE = "page"
}

@GenerateOpenApi
fun Application.describedHeadersTest() {
    routing {
        route("/v2") {
            get("/order1") {
                /** The API key issued to the client. */
                val apiKey = call.request.headers["X-Api-Key"]
                println(apiKey)
            }

            get("/order2") {
                // descriptions come from the KDoc on the referenced constants
                val tenant = call.request.headers[ApiHeaders.TENANT_ID]
                call.request.header(ApiHeaders.REQUEST_ID)
                println(tenant)
            }

            get("/order3") {
                /** Tenant override used only by this endpoint. */
                val tenant = call.request.headers[ApiHeaders.TENANT_ID]
                println(tenant)
            }

            get("/order4") {
                /** Preferred UI language. */
                val lang = call.request.acceptLanguage()
                val agent = call.request.userAgent()
                println("$lang $agent")
            }

            get("/order5") {
                // Accept, Content-Type and Authorization must not appear in the spec
                val auth = call.request.headers["Authorization"]
                val accept = call.request.header("Accept")
                val contentType = call.request.headers["Content-Type"]

                /** The only header expected in the spec for this endpoint. */
                val kept = call.request.headers["X-Kept"]
                println("$auth $accept $contentType $kept")
            }

            get("/order6") {
                val first = call.request.headers["X-Dup"]

                /** Documented on the second access. */
                val second = call.request.headers["X-Dup"]
                println("$first $second")
            }

            get("/order7") {
                /** Filter by order status. */
                val status = call.request.queryParameters["status"]
                call.request.queryParameters[ApiQueries.PAGE]
                println(status)
            }
        }
    }
}
