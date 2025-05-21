package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import sources.requests.SimpleRequest
import sources.precompiled.*


@GenerateOpenApi
fun Application.resourcesFromObjectConsts() {
    routing {
        route("/v1") {
            @KtorDescription("will query all")
            post<OrdersResources.Orders.QueryAll> {
                // success
                responds<SimpleRequest>(HttpStatusCode.OK)
                // bad reqeust
                responds<SimpleRequest>(HttpStatusCode.UnprocessableEntity)
            }
            @KtorDescription("will query latest")
            post<OrdersResources.Orders.QueryLatest> {
                // success 2
                responds<SimpleRequest>(HttpStatusCode.OK)
                // bad request 2
                responds<SimpleRequest>(HttpStatusCode.UnprocessableEntity)
            }
        }
    }
}
