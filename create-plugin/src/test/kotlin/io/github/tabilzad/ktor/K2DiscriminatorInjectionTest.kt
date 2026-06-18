package io.github.tabilzad.ktor

import io.github.tabilzad.ktor.TestUtils.loadSourceCodeFrom
import io.github.tabilzad.ktor.model.ConfigInput
import io.github.tabilzad.ktor.output.OpenApiSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Verifies that polymorphic (sealed) schemas emit a discriminator property inside every variant
 * referenced by the `mapping`, pinned to the same serial name used as the mapping key. Without this,
 * Redoc/Redocly collapse the `oneOf` to only the first variant.
 */
class K2DiscriminatorInjectionTest {

    @TempDir
    lateinit var tempDir: Path

    private val testFile get() = File(tempDir.toFile(), "openapi.json")

    @Test
    fun `should inject discriminator into each sealed variant pinned to its mapping key`() {
        val source = loadSourceCodeFrom("PaymentInputDiscriminator")
        generateCompilerTest(testFile, source, PluginConfiguration.createDefault())

        val spec = testFile.parseSpec()
        spec.assertDiscriminatorInjectedForEveryVariant("sources.PaymentInput")
    }

    @Test
    fun `should honor custom JsonClassDiscriminator name and align SerialName key with value`() {
        val source = loadSourceCodeFrom("PaymentInputDiscriminator")
        generateCompilerTest(testFile, source, PluginConfiguration.createDefault())

        val spec = testFile.parseSpec()
        val parent = spec.schema("sources.PaymentInput")
        assertThat(parent.discriminator?.propertyName).isEqualTo("__typename")

        // mapping key derives from @SerialName, and the injected variant value must equal it
        assertThat(parent.discriminator?.mapping)
            .containsEntry("CardPayment", "#/components/schemas/sources.CardPaymentInput")
            .containsEntry("CashPayment", "#/components/schemas/sources.CashPaymentInput")

        val card = spec.schema("sources.CardPaymentInput")
        assertThat(card.properties?.get("__typename")?.enum).containsExactly("CardPayment")
        assertThat(card.required).contains("__typename")

        // the base property still lives on a variant, the discriminator must NOT be on the parent
        assertThat(parent.properties).containsKey("amount")
        assertThat(parent.properties).doesNotContainKey("__typename")
    }

    @Test
    fun `should inject the config discriminator name when JsonClassDiscriminator is absent`() {
        val source = loadSourceCodeFrom("CustomDiscriminator")
        generateCompilerTest(
            testFile, source,
            PluginConfiguration.createDefault(initConfig = ConfigInput(discriminator = "__myCustomDiscriminator"))
        )

        val spec = testFile.parseSpec()
        val parent = spec.schema("sources.Action")
        assertThat(parent.discriminator?.propertyName).isEqualTo("__myCustomDiscriminator")
        spec.assertDiscriminatorInjectedForEveryVariant("sources.Action")

        // no @SerialName -> mapping key (and therefore injected value) is the fully qualified name
        val variant = spec.schema("sources.ActionOne")
        assertThat(variant.properties?.get("__myCustomDiscriminator")?.enum)
            .containsExactly("sources.ActionOne")
    }

    @Test
    fun `should reconcile and not duplicate when a variant already declares the discriminator field`() {
        val source = loadSourceCodeFrom("DiscriminatorAlreadyDeclared")
        generateCompilerTest(testFile, source, PluginConfiguration.createDefault())

        val spec = testFile.parseSpec()
        spec.assertDiscriminatorInjectedForEveryVariant("sources.Shape")

        val square = spec.schema("sources.Shape.Square")
        val typeProp = square.properties?.get("type")
        // the user's plain `type: String` is reconciled to the pinned single value
        assertThat(typeProp?.type).isEqualTo("string")
        assertThat(typeProp?.enum).containsExactly("sources.Shape.Square")
        // present exactly once in required (no duplication), and the side field is preserved
        assertThat(square.required?.count { it == "type" }).isEqualTo(1)
        assertThat(square.properties).containsKey("side")
    }

    private fun OpenApiSpec.schema(name: String): OpenApiSpec.TypeDescriptor =
        components.schemas[name] ?: error("schema '$name' not found in $components")

    /**
     * For a sealed parent, asserts every variant referenced by the discriminator `mapping` declares
     * the discriminator property as a required, single-value `enum` string equal to its mapping key.
     */
    private fun OpenApiSpec.assertDiscriminatorInjectedForEveryVariant(parentName: String) {
        val parent = schema(parentName)
        val discriminator = parent.discriminator ?: error("$parentName has no discriminator")
        val propertyName = discriminator.propertyName
        assertThat(discriminator.mapping).isNotEmpty

        discriminator.mapping.forEach { (mappingKey, ref) ->
            val variantName = ref.removePrefix("#/components/schemas/")
            val variant = schema(variantName)
            val property = variant.properties?.get(propertyName)
            assertThat(property)
                .`as`("variant '$variantName' must declare discriminator property '$propertyName'")
                .isNotNull
            assertThat(property!!.type).isEqualTo("string")
            assertThat(property.enum)
                .`as`("variant '$variantName' discriminator must be pinned to its mapping key")
                .containsExactly(mappingKey)
            assertThat(variant.required)
                .`as`("variant '$variantName' must require discriminator property '$propertyName'")
                .contains(propertyName)
        }
    }
}
