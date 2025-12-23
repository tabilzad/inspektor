# Configuration

InspeKtor is configured through the `swagger` block in your `build.gradle.kts`. This section covers all available
configuration options.

## Configuration Structure

```kotlin title="build.gradle.kts"
swagger {
    // API documentation settings
    documentation {
        info { /* API metadata */ }
        security { /* Security schemes */ }
        serialOverrides { /* Type mappings */ }
        // ... other options
    }

    // Plugin behavior settings
    pluginOptions {
        enabled = true
        format = "yaml"
        // ... other options
    }
}
```

## Quick Reference

| Category                                          | Purpose                                      |
|---------------------------------------------------|----------------------------------------------|
| [Documentation Options](documentation-options.md) | API info, schema generation settings         |
| [Plugin Options](plugin-options.md)               | Output format, file location, build behavior |
| [Security Schemes](security-schemes.md)           | JWT, API Key, OAuth2 configuration           |
| [Type Overrides](type-overrides.md)               | Custom type mappings for dates, UUIDs, etc.  |

## Minimal Configuration

For most projects, a minimal configuration is sufficient:

```kotlin title="build.gradle.kts"
swagger {
    documentation {
        info {
            title = "My API"
            version = "1.0.0"
        }
    }
}
```

## Full Configuration Example

Here's a comprehensive example showing all available options:

```kotlin title="build.gradle.kts"
swagger {
    documentation {
        // API Information
        info {
            title = "My Awesome API"
            description = "A comprehensive API for managing resources"
            version = "2.0.0"
            contact {
                name = "API Support"
                url = "https://example.com/support"
                email = "support@example.com"
            }
            license {
                name = "Apache 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }

        // Server URLs
        servers = listOf(
            "https://api.example.com",
            "https://staging-api.example.com",
            "http://localhost:8080"
        )

        // Schema generation options
        generateRequestSchemas = true
        hideTransientFields = true
        hidePrivateAndInternalFields = true
        deriveFieldRequirementFromTypeNullability = true
        useKDocsForDescriptions = true
        polymorphicDiscriminator = "type"

        // Security configuration
        security {
            schemes {
                "bearerAuth" to SecurityScheme(
                    type = "http",
                    scheme = "bearer",
                    bearerFormat = "JWT"
                )
            }
            scopes {
                or { +"bearerAuth" }
            }
        }

        // Type overrides
        serialOverrides {
            typeOverride("java.time.Instant") {
                serializedAs = "string"
                format = "date-time"
            }
        }
    }

    pluginOptions {
        enabled = true
        saveInBuild = true
        format = "yaml"
        filePath = null // Use default
        regenerationMode = "strict"
    }
}
```

## Environment-Specific Configuration

You can use Gradle properties for environment-specific values:

```kotlin title="build.gradle.kts"
swagger {
    documentation {
        info {
            title = "My API"
            version = project.version.toString()
        }
        servers = listOf(
            findProperty("api.server.url")?.toString() ?: "http://localhost:8080"
        )
    }
}
```

```properties title="gradle.properties"
api.server.url=https://api.production.com
```
