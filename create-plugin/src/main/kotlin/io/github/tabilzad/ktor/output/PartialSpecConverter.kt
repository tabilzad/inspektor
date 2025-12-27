package io.github.tabilzad.ktor.output

import io.github.tabilzad.ktor.model.*

/**
 * Converts between OpenApiSpec (internal representation) and PartialOpenApiSpec (serializable for multi-module).
 */
internal object PartialSpecConverter {

    /**
     * Converts an OpenApiSpec to a PartialOpenApiSpec for multi-module storage.
     */
    fun toPartialSpec(spec: OpenApiSpec, moduleId: String): PartialOpenApiSpec {
        return PartialOpenApiSpec(
            moduleId = moduleId,
            openapi = spec.openapi,
            paths = spec.paths.mapValues { (_, methods) ->
                methods.mapValues { (_, path) -> path.toPartialPath() }
            },
            schemas = spec.components.schemas.mapValues { (_, descriptor) ->
                descriptor.toPartialDescriptor()
            },
            securitySchemes = spec.components.securitySchemes ?: emptyMap()
        )
    }

    /**
     * Converts a PartialOpenApiSpec back to an OpenApiSpec for merging.
     */
    fun fromPartialSpec(partial: PartialOpenApiSpec): OpenApiSpec {
        return OpenApiSpec(
            openapi = partial.openapi,
            info = null,
            servers = null,
            paths = partial.paths.mapValues { (_, methods) ->
                methods.mapValues { (_, path) -> path.toOpenApiPath() }
            },
            components = OpenApiSpec.OpenApiComponents(
                schemas = partial.schemas.mapValues { (_, descriptor) ->
                    descriptor.toOpenApiDescriptor()
                },
                securitySchemes = partial.securitySchemes.takeIf { it.isNotEmpty() }
            ),
            security = null
        )
    }

    /**
     * Merges multiple partial specs into a single OpenApiSpec.
     */
    @Suppress("NestedBlockDepth")
    fun mergePartialSpecs(
        partialSpecs: List<PartialOpenApiSpec>,
        localSpec: OpenApiSpec?
    ): OpenApiSpec {
        // Start with empty collections
        val mergedPaths = mutableMapOf<String, MutableMap<String, OpenApiSpec.Path>>()
        val mergedSchemas = mutableMapOf<String, OpenApiSpec.TypeDescriptor>()
        val mergedSecuritySchemes = mutableMapOf<String, SecurityScheme>()

        // Merge all partial specs
        for (partial in partialSpecs) {
            // Merge paths
            for ((path, methods) in partial.paths) {
                val existingMethods = mergedPaths.getOrPut(path) { mutableMapOf() }
                for ((method, operation) in methods) {
                    if (existingMethods.containsKey(method)) {
                        // Path+method conflict - log warning but use the newer one
                        println("Warning: Duplicate path $method $path from module ${partial.moduleId}")
                    }
                    existingMethods[method] = operation.toOpenApiPath()
                }
            }

            // Merge schemas
            for ((name, schema) in partial.schemas) {
                if (mergedSchemas.containsKey(name)) {
                    // Schema conflict - log warning but use the newer one
                    println("Warning: Duplicate schema $name from module ${partial.moduleId}")
                }
                mergedSchemas[name] = schema.toOpenApiDescriptor()
            }

            // Merge security schemes
            for ((name, scheme) in partial.securitySchemes) {
                if (mergedSecuritySchemes.containsKey(name)) {
                    // Security scheme conflict - log warning
                    println("Warning: Duplicate security scheme $name from module ${partial.moduleId}")
                }
                mergedSecuritySchemes[name] = scheme
            }
        }

        // Merge local spec if present
        if (localSpec != null) {
            for ((path, methods) in localSpec.paths) {
                val existingMethods = mergedPaths.getOrPut(path) { mutableMapOf() }
                for ((method, operation) in methods) {
                    existingMethods[method] = operation
                }
            }
            for ((name, schema) in localSpec.components.schemas) {
                mergedSchemas[name] = schema
            }
            localSpec.components.securitySchemes?.let { mergedSecuritySchemes.putAll(it) }
        }

        return OpenApiSpec(
            openapi = "3.1.0",
            info = localSpec?.info,
            servers = localSpec?.servers,
            paths = mergedPaths.mapValues { it.value.toMap() },
            components = OpenApiSpec.OpenApiComponents(
                schemas = mergedSchemas,
                securitySchemes = mergedSecuritySchemes.takeIf { it.isNotEmpty() }
            ),
            security = localSpec?.security
        )
    }

    // Extension functions for conversion

    /**
     * Converts BodyContent to the serializable partial format. Content-type keys are plain
     * strings and pass through unchanged, preserving custom types such as "application/pdf".
     */
    private fun BodyContent.toPartialContent(): Map<String, Map<String, PartialTypeDescriptor>> =
        mapValues { (_, schemaMap) ->
            schemaMap.mapValues { (_, schema) -> schema.toPartialDescriptor() }
        }

    /**
     * Converts partial content format back to BodyContent.
     */
    private fun Map<String, Map<String, PartialTypeDescriptor>>.toBodyContent(): BodyContent =
        mapValues { (_, schemaMap) ->
            schemaMap.mapValues { (_, schema) -> schema.toOpenApiDescriptor() }
        }

    private fun OpenApiSpec.Path.toPartialPath(): PartialPath {
        return PartialPath(
            summary = summary,
            description = description,
            operationId = operationId,
            tags = tags,
            responses = responses?.mapValues { (_, response) ->
                PartialResponse(
                    description = response.description,
                    content = response.content?.toPartialContent()
                )
            },
            parameters = parameters?.map { it.toPartialParameter() },
            requestBody = requestBody?.let {
                PartialRequestBody(
                    required = it.required,
                    content = it.content.toPartialContent()
                )
            },
            security = security,
            deprecated = deprecated
        )
    }

    private fun PartialPath.toOpenApiPath(): OpenApiSpec.Path {
        return OpenApiSpec.Path(
            summary = summary,
            description = description,
            operationId = operationId,
            tags = tags,
            responses = responses?.mapValues { (_, response) ->
                OpenApiSpec.ResponseDetails(
                    description = response.description,
                    content = response.content?.toBodyContent()
                )
            },
            parameters = parameters?.map { it.toOpenApiParameter() },
            requestBody = requestBody?.let {
                OpenApiSpec.RequestBody(
                    required = it.required,
                    content = it.content?.toBodyContent() ?: emptyMap()
                )
            },
            security = security,
            deprecated = deprecated
        )
    }

    private fun OpenApiSpec.Parameter.toPartialParameter(): PartialParameter {
        return PartialParameter(
            name = name,
            `in` = `in`,
            required = required,
            description = description,
            schema = schema.toPartialDescriptor()
        )
    }

    private fun PartialParameter.toOpenApiParameter(): OpenApiSpec.Parameter {
        return OpenApiSpec.Parameter(
            name = name,
            `in` = `in`,
            required = required,
            description = description,
            schema = schema?.toOpenApiDescriptor() ?: OpenApiSpec.TypeDescriptor(type = "string")
        )
    }

    private fun OpenApiSpec.TypeDescriptor.toPartialDescriptor(): PartialTypeDescriptor {
        return PartialTypeDescriptor(
            type = type,
            properties = properties?.mapValues { (_, v) -> v.toPartialDescriptor() },
            items = items?.toPartialDescriptor(),
            enum = enum,
            fqName = fqName,
            description = description,
            ref = ref,
            additionalProperties = additionalProperties?.toPartialDescriptor(),
            oneOf = oneOf?.map { it.toPartialDescriptor() },
            required = required,
            format = format,
            discriminator = discriminator?.let {
                PartialDiscriminator(
                    propertyName = it.propertyName,
                    mapping = it.mapping
                )
            }
        )
    }

    private fun PartialTypeDescriptor.toOpenApiDescriptor(): OpenApiSpec.TypeDescriptor {
        return OpenApiSpec.TypeDescriptor(
            type = type,
            properties = properties?.mapValues { (_, v) -> v.toOpenApiDescriptor() }?.toMutableMap(),
            items = items?.toOpenApiDescriptor(),
            enum = enum,
            fqName = fqName,
            description = description,
            ref = ref,
            additionalProperties = additionalProperties?.toOpenApiDescriptor(),
            oneOf = oneOf?.map { it.toOpenApiDescriptor() },
            required = required?.toMutableList(),
            format = format,
            discriminator = discriminator?.let {
                OpenApiSpec.DiscriminatorDescriptor(
                    propertyName = it.propertyName,
                    mapping = it.mapping
                )
            }
        )
    }
}
