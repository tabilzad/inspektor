package com.example.api

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.annotations.KtorField
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

data class Item(val id: Int, val name: String, val description: String?, val priceInCents: Int)

data class CreateItemRequest(
    @KtorField(description = "Name of the item")
    val name: String,
    @KtorField(description = "Item description")
    val description: String?,
    @KtorField(description = "Price in cents")
    val priceInCents: Int
)

@GenerateOpenApi
fun Application.itemsModule() {
    routing {
        route("/items") {
            @KtorDescription(summary = "Create a new item")
            post {
                val request = call.receive<CreateItemRequest>()
                call.respond(Item(1, request.name, request.description, request.priceInCents))
            }

            route("/{id}") {
                @KtorDescription(summary = "Get item by ID")
                get {
                    val id = call.parameters["id"]?.toIntOrNull() ?: return@get
                    call.respond(Item(id, "Sample", null, 100))
                }

                @KtorDescription(summary = "Update an existing item")
                put {
                    val id = call.parameters["id"]?.toIntOrNull() ?: return@put
                    val request = call.receive<CreateItemRequest>()
                    call.respond(Item(id, request.name, request.description, request.priceInCents))
                }
            }
        }
    }
}
