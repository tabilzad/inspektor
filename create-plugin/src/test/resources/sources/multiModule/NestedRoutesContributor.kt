package com.example.admin

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

data class AdminUser(val id: Int, val role: String)
data class AuditLog(val id: Int, val action: String, val timestamp: Long)

@GenerateOpenApi
fun Application.adminModule() {
    routing {
        route("/api") {
            route("/v1") {
                route("/admin") {
                    route("/users") {
                        @KtorDescription(summary = "List admin users")
                        get {
                            call.respond(listOf<AdminUser>())
                        }

                        @KtorDescription(summary = "Get admin user by ID")
                        get("/{id}") {
                            call.respond(AdminUser(1, "superadmin"))
                        }
                    }

                    route("/audit") {
                        @KtorDescription(summary = "Get audit logs")
                        get("/logs") {
                            call.respond(listOf<AuditLog>())
                        }
                    }
                }
            }
        }
    }
}
