package com.example.server

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

data class HealthStatus(val status: String, val uptime: Long)

@GenerateOpenApi
fun Application.mainModule() {
    routing {
        @KtorDescription(summary = "Health check endpoint")
        get("/health") {
            call.respond(HealthStatus("OK", 12345))
        }
    }
}
