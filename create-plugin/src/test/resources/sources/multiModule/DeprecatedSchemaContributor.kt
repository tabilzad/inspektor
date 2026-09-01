package com.example.billing

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/** An invoice kept for old billing clients. */
@Deprecated("Use Invoice instead")
data class LegacyInvoice(
    val id: String,
    @Deprecated("Use totalCents instead")
    val total: Double?
)

@GenerateOpenApi
fun Application.billingModule() {
    routing {
        route("/api/billing") {
            @KtorDescription(summary = "Create invoice")
            post("/invoices") {
                val invoice = call.receive<LegacyInvoice>()
                call.respond(invoice)
            }
        }
    }
}
