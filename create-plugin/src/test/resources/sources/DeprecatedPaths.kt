package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.ktor.server.application.*
import io.ktor.server.routing.*

@GenerateOpenApi
fun Application.routesWithSamePathButDifferentMethod() {
    routing {
        routeOneD()
    }
}

@GenerateOpenApi
@Deprecated("This module is deprecated")
fun Application.deprecatedModule() {
    routing {
        routeTwoD()
    }
}


fun Route.routeOneD() {
    route("/v1") {
        @KtorDescription("should not be deprecated")
        get("/getRequest1") {

        }
        @KtorDescription("should not be deprecated")
        post("/getRequest1") {

        }
    }
    routeThreeD()
}

fun Route.routeTwoD() {
    route("/v2") {
        @KtorDescription("should be deprecated")
        get("/getRequest2") {

        }
        @KtorDescription("should be deprecated")
        post("/getRequest2") {

        }
    }

}

@Deprecated("This route is deprecated, use routeTwoD instead")
fun Route.routeThreeD() {
    route("/v3") {
        routeThreeDSub()
        @KtorDescription("should be deprecated")
        get("/getRequest3") {

        }
        @KtorDescription("should be deprecated")
        post("/getRequest3") {

        }
    }
}

fun Route.routeThreeDSub() {
    route("/v3a") {
        @KtorDescription("should be deprecated")
        get("/getRequest3") {

        }
        @KtorDescription("should be deprecated")
        post("/getRequest3") {

        }
    }
}