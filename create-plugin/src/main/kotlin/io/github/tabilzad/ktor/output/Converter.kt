package io.github.tabilzad.ktor.output

import io.github.tabilzad.ktor.*
import org.jetbrains.kotlin.name.StandardClassIds

internal fun convertInternalToOpenSpec(
    routes: List<RouteDescriptor>,
    configuration: PluginConfiguration,
    schemas: Map<String, OpenApiSpec.TypeDescriptor>
): OpenApiSpec {
    // Flatten all routes from all RouteDescriptors into a single list
    val allRouteSpecs = routes.flatMap { reduce(it) }.cleanPaths()

    // Group by path and method, merging when the same path/method appears multiple times
    val mergedRouteSpecs = allRouteSpecs
        .groupBy { it.path to it.method }
        .map { (_, specsForPathMethod) ->
            // Merge all specs for the same path/method into one
            specsForPathMethod.reduce { acc, spec ->
                acc.copy(
                    tags = acc.tags merge spec.tags,
                    summary = acc.summary ?: spec.summary,
                    description = acc.description ?: spec.description,
                    operationId = acc.operationId ?: spec.operationId,
                    parameters = acc.parameters ?: spec.parameters,
                    responses = acc.responses ?: spec.responses,
                    deprecated = acc.deprecated ?: spec.deprecated
                )
            }
        }

    // Convert merged specs to OpenAPI format
    val reducedRoutes = mergedRouteSpecs
        .convertToSpec()
        .mapKeys { it.key.replace("//", "/") }

    return OpenApiSpec(
        info = configuration.initConfig.info,
        servers = configuration.servers.map { OpenApiSpec.Server(it) }.ifEmpty { null },
        paths = reducedRoutes,
        components = OpenApiSpec.OpenApiComponents(
            schemas = schemas.filter { (k, _) -> k != StandardClassIds.Nothing.asFqNameString() },
            securitySchemes = configuration.initConfig.securitySchemes.takeIf { it.isNotEmpty() },
        ),
        security = configuration.initConfig.securityConfig.takeIf { it.isNotEmpty() }
    )
}
