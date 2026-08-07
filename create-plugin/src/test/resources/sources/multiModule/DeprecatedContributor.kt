package com.example.legacy

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

@GenerateOpenApi
fun Application.legacyModule() {
    routing {
        legacyRoutes()
        currentRoutes()
    }
}

@Deprecated("Use /api/v2/items instead")
fun Route.legacyRoutes() {
    route("/api/v1") {
        @KtorDescription(summary = "Legacy endpoint")
        get("/items") { call.respond(listOf<String>()) }
    }
}

fun Route.currentRoutes() {
    route("/api/v2") {
        @KtorDescription(summary = "Current endpoint")
        get("/items") { call.respond(listOf<String>()) }
    }
}
