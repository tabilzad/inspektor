package com.example.orders

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

data class Order(val id: Int, val userId: Int, val total: Double)

@GenerateOpenApi
fun Application.ordersModule() {
    routing {
        route("/orders") {
            @KtorDescription(summary = "Get all orders")
            get {
                call.respond(listOf<Order>())
            }
        }
    }
}
