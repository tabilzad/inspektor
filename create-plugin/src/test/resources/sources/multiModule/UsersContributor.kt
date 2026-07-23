package com.example.users

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

data class User(val id: Int, val name: String, val email: String)
data class CreateUserRequest(val name: String, val email: String)

@GenerateOpenApi
fun Application.usersModule() {
    routing {
        route("/users") {
            @KtorDescription(summary = "Get all users", description = "Returns a list of all users")
            get {
                call.respond(listOf<User>())
            }

            @KtorDescription(summary = "Create user", description = "Creates a new user")
            post {
                val request = call.receive<CreateUserRequest>()
                call.respond(User(1, request.name, request.email))
            }

            @KtorDescription(summary = "Get user by ID")
            get("/{id}") {
                call.respond(User(1, "Test", "test@example.com"))
            }
        }
    }
}
