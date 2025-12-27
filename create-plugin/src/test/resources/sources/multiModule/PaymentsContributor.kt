package com.example.payments

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

data class Payment(val id: Int, val orderId: Int, val amount: Double, val status: String)
data class ProcessPaymentRequest(val orderId: Int, val amount: Double, val paymentMethod: String)

@GenerateOpenApi
fun Application.paymentsModule() {
    routing {
        route("/payments") {
            @KtorDescription(summary = "Process payment")
            post {
                val request = call.receive<ProcessPaymentRequest>()
                call.respond(Payment(1, request.orderId, request.amount, "processed"))
            }
        }
    }
}
