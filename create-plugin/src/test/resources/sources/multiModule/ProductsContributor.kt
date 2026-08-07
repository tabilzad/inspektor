package com.example.products

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

data class Product(val id: Int, val name: String, val price: Double)

@GenerateOpenApi
fun Application.productsModule() {
    routing {
        route("/products") {
            @KtorDescription(summary = "Get all products")
            get {
                call.respond(listOf<Product>())
            }

            @KtorDescription(summary = "Get product by ID")
            get("/{id}") {
                call.respond(Product(1, "Widget", 9.99))
            }
        }
    }
}
