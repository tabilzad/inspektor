package io.github.tabilzad.ktor

import io.github.tabilzad.ktor.output.OpenApiSpec
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe collector for OpenAPI specification data.
 *
 * During the FIR (analysis) phase, each @GenerateOpenApi function's routes and schemas
 * are collected here. During the IR (code generation) phase, all collected data is
 * consumed and written to a single file.
 *
 * Uses a static map keyed by configuration identity to avoid classloader issues
 * between FIR and IR phases. The CompilerConfiguration object itself is the same
 * instance across phases, even if this class is loaded by different classloaders.
 */
internal object OpenApiSpecCollector {

    /**
     * Collected route and schema data from a single @GenerateOpenApi function.
     */
    data class CollectedRouteData(
        val routes: List<RouteDescriptor>,
        val schemas: Map<String, OpenApiSpec.TypeDescriptor>
    )

    /**
     * Static map keyed by configuration's identity hash code.
     * This avoids classloader issues with CompilerConfigurationKey, since the
     * configuration object identity is preserved across FIR and IR phases.
     */
    private val globalData = ConcurrentHashMap<Int, MutableMap<String, MutableList<CollectedRouteData>>>()

    /**
     * Get or create the data map for a specific configuration.
     * Uses System.identityHashCode to key by configuration identity.
     */
    private fun getDataMap(configuration: CompilerConfiguration): MutableMap<String, MutableList<CollectedRouteData>> {
        val configId = System.identityHashCode(configuration)
        return globalData.getOrPut(configId) { ConcurrentHashMap() }
    }

    /**
     * Collect routes and schemas from a single @GenerateOpenApi function.
     * Called during FIR phase for each annotated function.
     */
    fun collect(
        configuration: CompilerConfiguration,
        key: String,
        routes: List<RouteDescriptor>,
        schemas: Map<String, OpenApiSpec.TypeDescriptor>
    ) {
        val dataMap = getDataMap(configuration)
        dataMap
            .getOrPut(key) { Collections.synchronizedList(mutableListOf()) }
            .add(CollectedRouteData(routes, schemas))
    }

    /**
     * Consume and remove all collected data for a given key.
     * Called during IR phase to retrieve all data before writing.
     * Also cleans up the configuration entry if empty to prevent memory leaks.
     */
    fun consumeAll(configuration: CompilerConfiguration, key: String): List<CollectedRouteData> {
        val configId = System.identityHashCode(configuration)
        val dataMap = globalData[configId] ?: return emptyList()
        val result = dataMap.remove(key) ?: emptyList()

        // Clean up configuration entry if no more data
        if (dataMap.isEmpty()) {
            globalData.remove(configId)
        }

        return result
    }
}
