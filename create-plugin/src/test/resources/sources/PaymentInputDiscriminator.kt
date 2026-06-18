package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonClassDiscriminator
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

@JsonClassDiscriminator("__typename")
sealed class PaymentInput {
    abstract val amount: Int
}

@SerialName("CardPayment")
data class CardPaymentInput(override val amount: Int, val last4: String) : PaymentInput()

@SerialName("CashPayment")
data class CashPaymentInput(override val amount: Int, val tendered: Int) : PaymentInput()

@GenerateOpenApi
fun Application.modulePaymentInput() {
    routing {
        route("/v1") {
            post("/payment") {
                call.receive<PaymentInput>()
            }
        }
    }
}
