package com.example.feature

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

@GenerateOpenApi
fun Application.featureModule() {
    routing {
        route("/shared") {
            @KtorDescription(summary = "Shared endpoint from contributor")
            get {
                call.respond("From contributor")
            }
        }
    }
}
