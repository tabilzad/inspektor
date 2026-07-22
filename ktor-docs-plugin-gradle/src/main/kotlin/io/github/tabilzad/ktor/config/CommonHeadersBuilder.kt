package io.github.tabilzad.ktor.config

import io.github.tabilzad.ktor.model.CommonHeaderConfig

/**
 * Builder for headers that apply to every generated operation. Use this for cross-cutting
 * headers consumed by middleware/interceptors (correlation ids, tenant/client identity, etc.)
 * that route handlers never read explicitly, so automatic inference cannot discover them.
 *
 * ```kotlin
 * swagger {
 *     documentation {
 *         commonHeaders {
 *             header("X-Request-Id", description = "Correlation id propagated across services")
 *             header("X-Tenant-Id", description = "Tenant the request is scoped to", required = true)
 *         }
 *     }
 * }
 * ```
 */
class CommonHeadersBuilder {
    private val headers = mutableListOf<CommonHeaderConfig>()

    @JvmOverloads
    fun header(name: String, description: String? = null, required: Boolean = false) {
        headers.add(CommonHeaderConfig(name = name, description = description, required = required))
    }

    fun build(): List<CommonHeaderConfig> = headers.toList()
}
