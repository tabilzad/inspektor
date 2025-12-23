# Gradle DSL

Complete reference for InspeKtor's Gradle configuration.

## Overview

InspeKtor is configured through the `swagger` block in your `build.gradle.kts`:

```kotlin
swagger {
    documentation {
        // API metadata and schema options
    }
    pluginOptions {
        // Plugin behavior settings
    }
}
```

---

## swagger { }

Root configuration block for InspeKtor.

```kotlin
swagger {
    documentation { }
    pluginOptions { }
}
```

---

## documentation { }

Configures API metadata and schema generation.

### info { }

API metadata that appears in the OpenAPI spec.

```kotlin
documentation {
    info {
        title = "My API"
        description = "API description"
        version = "1.0.0"

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
}
```

#### Info Properties

| Property      | Type     | Default                              | Description     |
|---------------|----------|--------------------------------------|-----------------|
| `title`       | `String` | `"Open API Specification"`           | API title       |
| `description` | `String` | `"Generated using Ktor Docs Plugin"` | API description |
| `version`     | `String` | `"1.0.0"`                            | API version     |

#### Contact Properties

| Property | Type      | Default | Description   |
|----------|-----------|---------|---------------|
| `name`   | `String?` | `null`  | Contact name  |
| `url`    | `String?` | `null`  | Contact URL   |
| `email`  | `String?` | `null`  | Contact email |

#### License Properties

| Property | Type      | Default | Description  |
|----------|-----------|---------|--------------|
| `name`   | `String?` | `null`  | License name |
| `url`    | `String?` | `null`  | License URL  |

---

### servers

Server URLs where the API is hosted.

```kotlin
documentation {
    servers = listOf(
        "https://api.example.com",
        "https://staging.example.com",
        "http://localhost:8080"
    )
}
```

| Property  | Type           | Default | Description         |
|-----------|----------------|---------|---------------------|
| `servers` | `List<String>` | `[]`    | List of server URLs |

---

### Schema Generation Options

Options that control how schemas are generated from your Kotlin types.

```kotlin
documentation {
    generateRequestSchemas = true
    hideTransientFields = true
    hidePrivateAndInternalFields = true
    deriveFieldRequirementFromTypeNullability = true
    useKDocsForDescriptions = true
    polymorphicDiscriminator = "type"
}
```

| Property                                    | Type      | Default  | Description                                    |
|---------------------------------------------|-----------|----------|------------------------------------------------|
| `generateRequestSchemas`                    | `Boolean` | `true`   | Auto-generate schemas from `call.receive<T>()` |
| `hideTransientFields`                       | `Boolean` | `true`   | Exclude `@Transient` fields                    |
| `hidePrivateAndInternalFields`              | `Boolean` | `true`   | Exclude private/internal fields                |
| `deriveFieldRequirementFromTypeNullability` | `Boolean` | `true`   | Non-null = required                            |
| `useKDocsForDescriptions`                   | `Boolean` | `true`   | Extract KDoc comments                          |
| `polymorphicDiscriminator`                  | `String`  | `"type"` | Sealed class discriminator name                |

---

### security { }

Configure authentication schemes.

```kotlin
documentation {
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
}
```

#### schemes { }

Define security schemes.

```kotlin
schemes {
    "schemeName" to SecurityScheme(
        type = "http",           // http, apiKey, oauth2, openIdConnect
        scheme = "bearer",       // For http: bearer, basic
        bearerFormat = "JWT",    // Optional format hint
        `in` = "header",         // For apiKey: header, query, cookie
        name = "X-API-Key",      // For apiKey: parameter name
        description = "...",     // Optional description
        flows = OAuthFlows(...
    ), // For oauth2
    openIdConnectUrl = "..." // For openIdConnect
    )
}
```

#### SecurityScheme Properties

| Property           | Type          | Description                                         |
|--------------------|---------------|-----------------------------------------------------|
| `type`             | `String`      | `"http"`, `"apiKey"`, `"oauth2"`, `"openIdConnect"` |
| `scheme`           | `String?`     | HTTP scheme: `"bearer"`, `"basic"`                  |
| `bearerFormat`     | `String?`     | Format hint: `"JWT"`                                |
| `in`               | `String?`     | API key location: `"header"`, `"query"`, `"cookie"` |
| `name`             | `String?`     | API key parameter name                              |
| `description`      | `String?`     | Scheme description                                  |
| `flows`            | `OAuthFlows?` | OAuth 2.0 flow configuration                        |
| `openIdConnectUrl` | `String?`     | OpenID Connect discovery URL                        |

#### scopes { }

Define global security requirements.

```kotlin
scopes {
    // OR logic - any scheme works
    or { +"bearerAuth" }
    or { +"apiKeyAuth" }

    // AND logic - all required
    or {
        +"bearerAuth"
        +"apiKeyAuth"
    }

    // With OAuth scopes
    or {
        +"oauth2" requires listOf("read:users", "write:users")
    }
}
```

---

### serialOverrides { }

Custom type mappings for the OpenAPI schema.

```kotlin
documentation {
    serialOverrides {
        typeOverride("java.time.Instant") {
            serializedAs = "string"
            format = "date-time"
        }

        typeOverride("java.util.UUID") {
            serializedAs = "string"
            format = "uuid"
        }
    }
}
```

#### typeOverride Properties

| Property       | Type      | Description                                                    |
|----------------|-----------|----------------------------------------------------------------|
| `serializedAs` | `String`  | OpenAPI type: `"string"`, `"integer"`, `"number"`, `"boolean"` |
| `format`       | `String?` | OpenAPI format: `"date-time"`, `"uuid"`, `"email"`, etc.       |
| `pattern`      | `String?` | Regex validation pattern                                       |
| `description`  | `String?` | Type description                                               |
| `example`      | `String?` | Example value                                                  |
| `minimum`      | `Number?` | Minimum value (numbers)                                        |
| `maximum`      | `Number?` | Maximum value (numbers)                                        |
| `minLength`    | `Int?`    | Minimum length (strings)                                       |
| `maxLength`    | `Int?`    | Maximum length (strings)                                       |

---

## pluginOptions { }

Controls plugin behavior.

```kotlin
pluginOptions {
    enabled = true
    saveInBuild = true
    format = "yaml"
    filePath = null
    regenerationMode = "strict"
}
```

| Property           | Type      | Default    | Description                         |
|--------------------|-----------|------------|-------------------------------------|
| `enabled`          | `Boolean` | `true`     | Enable/disable spec generation      |
| `saveInBuild`      | `Boolean` | `true`     | Save in `build/` directory          |
| `format`           | `String`  | `"yaml"`   | Output format: `"yaml"` or `"json"` |
| `filePath`         | `String?` | `null`     | Custom output path (absolute)       |
| `regenerationMode` | `String`  | `"strict"` | Incremental build mode              |

### regenerationMode Values

| Mode       | Description                                     |
|------------|-------------------------------------------------|
| `"strict"` | Always regenerate (recommended for CI)          |
| `"safe"`   | Regenerate when `@GenerateOpenApi` files change |
| `"fast"`   | Trust incremental compilation                   |

---

## Complete Example

```kotlin title="build.gradle.kts"
plugins {
    kotlin("jvm") version "2.3.0"
    id("io.ktor.plugin") version "3.0.0"
    id("io.github.tabilzad.inspektor") version "0.10.0-alpha"
}

swagger {
    documentation {
        info {
            title = "My Awesome API"
            description = """
                A comprehensive REST API for managing resources.

                ## Features
                - User management
                - Product catalog
                - Order processing
            """
            version = project.version.toString()

            contact {
                name = "API Support Team"
                url = "https://example.com/support"
                email = "api-support@example.com"
            }

            license {
                name = "Apache 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }

        servers = listOf(
            "https://api.example.com",
            "https://staging.example.com",
            "http://localhost:8080"
        )

        // Schema options
        generateRequestSchemas = true
        hideTransientFields = true
        hidePrivateAndInternalFields = true
        deriveFieldRequirementFromTypeNullability = true
        useKDocsForDescriptions = true
        polymorphicDiscriminator = "type"

        // Security
        security {
            schemes {
                "bearerAuth" to SecurityScheme(
                    type = "http",
                    scheme = "bearer",
                    bearerFormat = "JWT",
                    description = "JWT token from /auth/login"
                )

                "apiKey" to SecurityScheme(
                    type = "apiKey",
                    `in` = "header",
                    name = "X-API-Key",
                    description = "API key for service accounts"
                )
            }

            scopes {
                or { +"bearerAuth" }
                or { +"apiKey" }
            }
        }

        // Type mappings
        serialOverrides {
            typeOverride("java.time.Instant") {
                serializedAs = "string"
                format = "date-time"
            }
            typeOverride("java.time.LocalDate") {
                serializedAs = "string"
                format = "date"
            }
            typeOverride("java.util.UUID") {
                serializedAs = "string"
                format = "uuid"
            }
            typeOverride("java.math.BigDecimal") {
                serializedAs = "string"
                format = "decimal"
            }
        }
    }

    pluginOptions {
        enabled = true
        saveInBuild = true
        format = "yaml"
        regenerationMode = if (System.getenv("CI") != null) "strict" else "safe"
    }
}
```

---

## Environment-Based Configuration

### Using Gradle Properties

```kotlin
swagger {
    documentation {
        info {
            version = project.version.toString()
        }
        servers = listOf(
            findProperty("api.server.url")?.toString() ?: "http://localhost:8080"
        )
    }
    pluginOptions {
        regenerationMode = findProperty("openapi.mode")?.toString() ?: "strict"
    }
}
```

```properties title="gradle.properties"
api.server.url=https://api.production.com
openapi.mode=strict
```

### Using Environment Variables

```kotlin
swagger {
    pluginOptions {
        regenerationMode = when {
            System.getenv("CI") != null -> "strict"
            System.getenv("QUICK_BUILD") != null -> "fast"
            else -> "safe"
        }
    }
}
```

### Conditional Configuration

```kotlin
swagger {
    pluginOptions {
        enabled = !project.hasProperty("skipOpenApi")
    }
}
```

Run with: `./gradlew build -PskipOpenApi`
