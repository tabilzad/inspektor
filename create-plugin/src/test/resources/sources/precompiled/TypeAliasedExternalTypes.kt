package sources.precompiled

import io.github.tabilzad.ktor.annotations.KtorSchema

data class ExternalMoney(val amount: Long, val currency: String)

@KtorSchema(description = "External money as its JSON surrogate")
data class ExternalJsonMoney(val valueInLowestDenomination: Long, val currencyCode: String)

// Declaration-site annotation on a typealias that reaches the consuming module only through
// compiled class files (Kotlin metadata keeps both the alias and its annotations).
@KtorSchema(serializedAs = ExternalJsonMoney::class)
typealias ExternalSerializableMoney = ExternalMoney

data class ExternalPricedItem(
    val price: ExternalSerializableMoney,
    val upcharge: ExternalSerializableMoney?,
    val allocatedPrices: List<ExternalSerializableMoney>?
)
