package io.github.tabilzad.ktor.output

import io.github.tabilzad.ktor.*
import org.jetbrains.kotlin.name.StandardClassIds

internal fun convertInternalToOpenSpec(
    routes: List<DocRoute>,
    configuration: PluginConfiguration,
    schemas: Map<String, OpenApiSpec.TypeDescriptor>
): OpenApiSpec {
    val reducedRoutes = routes
        .map {
            reduce(it)
                .cleanPaths()
                .convertToSpec()
        }
        .reduce { acc, route ->
            acc.plus(route)
        }.mapKeys { it.key.replace("//", "/") }

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
