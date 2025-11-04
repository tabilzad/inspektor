package io.github.tabilzad.ktor.config

import io.github.tabilzad.ktor.model.SecurityScheme
import org.gradle.api.Action

data class SecurityConfig(
    val scopes: List<Map<String, List<String>>>,
    val schemes: Map<String, SecurityScheme>,
)

class SecurityBuilder {
    private val requirements = mutableListOf<Map<String, List<String>>>()
    private val schemes = mutableMapOf<String, SecurityScheme>()

    fun scopes(action: Action<ScopeConfigBuilder>) {
        val scopeConfig = ScopeConfigBuilder().also(action::execute).build()
        requirements.addAll(scopeConfig)
    }

    fun schemes(action: Action<SchemeConfigBuilder>) {
        val schemeConfig = SchemeConfigBuilder().also(action::execute).build()
        schemes.putAll(schemeConfig)
    }

    fun build(): SecurityConfig = SecurityConfig(requirements, schemes.toMap())
}

class ScopeConfigBuilder {
    private val items = mutableListOf<Map<String, List<String>>>()

    fun or(action: Action<ItemBuilder>) {
        val builder = ItemBuilder()
        action.execute(builder)
        items.add(builder.build())
    }

    fun and(action: Action<ItemBuilder>) {
        val builder = ItemBuilder()
        action.execute(builder)
        items.add(builder.build())
    }

    fun build(): List<Map<String, List<String>>> = items
}

class ItemBuilder {
    private val map = linkedMapOf<String, List<String>>()

    operator fun String.unaryPlus() {
        map[this] = emptyList()
    }

    infix fun String.to(scopes: List<String>) {
        map[this] = scopes
    }

    fun build(): Map<String, List<String>> = map
}

class SchemeConfigBuilder {
    private val configs = mutableMapOf<String, SecurityScheme>()

    infix fun String.to(scheme: SecurityScheme) {
        configs[this] = scheme
    }

    fun build(): Map<String, SecurityScheme> = configs
}
