package io.github.tabilzad.ktor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KDocParserTest {

    @Test
    fun `plain kdoc has text and no property docs`() {
        val parsed = parseKDoc("Represents a user in the system.")

        assertThat(parsed.text).isEqualTo("Represents a user in the system.")
        assertThat(parsed.propertyDocs).isEmpty()
    }

    @Test
    fun `property tags map to field docs and are excluded from class text`() {
        val parsed = parseKDoc(
            """
            OrderRequest data is used to create orders from external sources.

            @property cart Represents the cart used to create an order
            @property currencyCode ISO 4217 currency code for this currency.
            """.trimIndent()
        )

        assertThat(parsed.text).isEqualTo("OrderRequest data is used to create orders from external sources.")
        assertThat(parsed.propertyDocs).containsEntry("cart", "Represents the cart used to create an order")
        assertThat(parsed.propertyDocs).containsEntry("currencyCode", "ISO 4217 currency code for this currency.")
    }

    @Test
    fun `multi-line property descriptions are joined`() {
        val parsed = parseKDoc(
            """
            Class text.

            @property customerInfo Customer info contains optional data about customer,
                such as name and loyalty status.
            @property occasion Specifies occasion type.
            """.trimIndent()
        )

        assertThat(parsed.propertyDocs["customerInfo"])
            .isEqualTo("Customer info contains optional data about customer, such as name and loyalty status.")
        assertThat(parsed.propertyDocs["occasion"]).isEqualTo("Specifies occasion type.")
    }

    @Test
    fun `param tags are treated like property tags`() {
        val parsed = parseKDoc("@param employeeId Manager ID to be associated with the shift.")

        assertThat(parsed.text).isNull()
        assertThat(parsed.propertyDocs["employeeId"]).isEqualTo("Manager ID to be associated with the shift.")
    }

    @Test
    fun `other tags end accumulation and are not class text`() {
        val parsed = parseKDoc(
            """
            Class text.
            @property id The identifier.
            @throws IllegalStateException when broken
            trailing junk after unknown tag
            """.trimIndent()
        )

        assertThat(parsed.text).isEqualTo("Class text.")
        assertThat(parsed.propertyDocs).containsOnlyKeys("id")
        assertThat(parsed.propertyDocs["id"]).isEqualTo("The identifier.")
    }

    @Test
    fun `null and blank input yield empty result`() {
        assertThat(parseKDoc(null)).isEqualTo(ParsedKDoc(null, emptyMap()))
        assertThat(parseKDoc("   ")).isEqualTo(ParsedKDoc(null, emptyMap()))
    }
}
