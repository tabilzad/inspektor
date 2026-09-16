package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorSchema
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

data class Money(val amount: Long, val currency: String)

@KtorSchema(description = "Money as its JSON surrogate")
data class JsonMoney(val valueInLowestDenomination: Long, val currencyCode: String)

// The annotation sits on the typealias declaration, not on the aliased type.
@KtorSchema(serializedAs = JsonMoney::class)
typealias SerializableMoney = Money

data class PricedItem(
    val price: SerializableMoney,
    val upcharge: SerializableMoney?,
    val allocatedPrices: List<SerializableMoney>?
)

@GenerateOpenApi
fun Application.responseBody() {
    routing {
        route("/v3") {
            post("/pricedItem") {
                call.receive<PricedItem>()
            }
        }
    }
}
