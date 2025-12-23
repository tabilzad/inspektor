# Security Schemes

Configure authentication and authorization documentation for your API.

## Overview

InspeKtor supports all OpenAPI security schemes:

- **HTTP Authentication** (Bearer, Basic)
- **API Keys** (Header, Query, Cookie)
- **OAuth 2.0** (All flows)
- **OpenID Connect**

## Basic Setup

```kotlin title="build.gradle.kts"
swagger {
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
                // Apply to all endpoints by default
                or { +"bearerAuth" }
            }
        }
    }
}
```

## HTTP Authentication

### Bearer Token (JWT)

The most common authentication scheme for modern APIs:

```kotlin
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
```

Generated OpenAPI:

```yaml
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
security:
  - bearerAuth: []
```

### Basic Authentication

```kotlin
security {
    schemes {
        "basicAuth" to SecurityScheme(
            type = "http",
            scheme = "basic"
        )
    }
    scopes {
        or { +"basicAuth" }
    }
}
```

## API Key Authentication

### Header API Key

```kotlin
security {
    schemes {
        "apiKeyAuth" to SecurityScheme(
            type = "apiKey",
            `in` = "header",
            name = "X-API-Key"
        )
    }
    scopes {
        or { +"apiKeyAuth" }
    }
}
```

### Query Parameter API Key

```kotlin
security {
    schemes {
        "apiKeyAuth" to SecurityScheme(
            type = "apiKey",
            `in` = "query",
            name = "api_key"
        )
    }
}
```

### Cookie API Key

```kotlin
security {
    schemes {
        "sessionAuth" to SecurityScheme(
            type = "apiKey",
            `in` = "cookie",
            name = "session_id"
        )
    }
}
```

## OAuth 2.0

### Authorization Code Flow

```kotlin
security {
    schemes {
        "oauth2" to SecurityScheme(
            type = "oauth2",
            flows = OAuthFlows(
                authorizationCode = OAuthFlow(
                    authorizationUrl = "https://auth.example.com/authorize",
                    tokenUrl = "https://auth.example.com/token",
                    scopes = mapOf(
                        "read:users" to "Read user information",
                        "write:users" to "Modify user information",
                        "admin" to "Full administrative access"
                    )
                )
            )
        )
    }
    scopes {
        or {
            +"oauth2" requires listOf("read:users")
        }
    }
}
```

### Client Credentials Flow

For machine-to-machine authentication:

```kotlin
security {
    schemes {
        "oauth2-client" to SecurityScheme(
            type = "oauth2",
            flows = OAuthFlows(
                clientCredentials = OAuthFlow(
                    tokenUrl = "https://auth.example.com/token",
                    scopes = mapOf(
                        "api:read" to "Read access to API",
                        "api:write" to "Write access to API"
                    )
                )
            )
        )
    }
}
```

### Implicit Flow

!!! warning "Deprecated"
    The implicit flow is deprecated for security reasons. Use Authorization Code with PKCE instead.

```kotlin
security {
    schemes {
        "oauth2-implicit" to SecurityScheme(
            type = "oauth2",
            flows = OAuthFlows(
                implicit = OAuthFlow(
                    authorizationUrl = "https://auth.example.com/authorize",
                    scopes = mapOf(
                        "read" to "Read access",
                        "write" to "Write access"
                    )
                )
            )
        )
    }
}
```

## OpenID Connect

```kotlin
security {
    schemes {
        "openIdConnect" to SecurityScheme(
            type = "openIdConnect",
            openIdConnectUrl = "https://auth.example.com/.well-known/openid-configuration"
        )
    }
}
```

## Combining Security Schemes

### OR Logic (Any scheme works)

Use multiple `or` blocks or multiple schemes in one block:

```kotlin
scopes {
    // User can authenticate with EITHER bearer OR api key
    or { +"bearerAuth" }
    or { +"apiKeyAuth" }
}
```

Generated:

```yaml
security:
  - bearerAuth: []
  - apiKeyAuth: []
```

### AND Logic (All schemes required)

Put multiple schemes in the same `or` block:

```kotlin
scopes {
    // User must provide BOTH bearer token AND api key
    or {
        +"bearerAuth"
        +"apiKeyAuth"
    }
}
```

Generated:

```yaml
security:
  - bearerAuth: []
    apiKeyAuth: []
```

## Per-Endpoint Security

Override global security on specific endpoints using annotations:

```kotlin
@Tag(["Public"])
fun Route.publicRoutes() {
    // This endpoint has no security requirement
    @KtorDescription(summary = "Health check")
    get("/health") {
        // No authentication required
    }
}

@Tag(["Admin"])
fun Route.adminRoutes() {
    // These endpoints require admin scope
    route("/admin") {
        @KtorDescription(summary = "List all users")
        get("/users") {
            // Requires admin authentication
        }
    }
}
```

## Complete Example

Here's a comprehensive security configuration:

```kotlin title="build.gradle.kts"
swagger {
    documentation {
        info {
            title = "Secure API"
            version = "1.0.0"
        }

        security {
            schemes {
                // JWT for user authentication
                "bearerAuth" to SecurityScheme(
                    type = "http",
                    scheme = "bearer",
                    bearerFormat = "JWT",
                    description = "JWT token obtained from /auth/login"
                )

                // API key for service-to-service
                "apiKey" to SecurityScheme(
                    type = "apiKey",
                    `in` = "header",
                    name = "X-API-Key",
                    description = "API key for service accounts"
                )

                // OAuth2 for third-party integrations
                "oauth2" to SecurityScheme(
                    type = "oauth2",
                    flows = OAuthFlows(
                        authorizationCode = OAuthFlow(
                            authorizationUrl = "https://auth.example.com/authorize",
                            tokenUrl = "https://auth.example.com/token",
                            scopes = mapOf(
                                "read" to "Read access",
                                "write" to "Write access",
                                "admin" to "Administrative access"
                            )
                        )
                    )
                )
            }

            scopes {
                // Default: require bearer auth OR api key
                or { +"bearerAuth" }
                or { +"apiKey" }
            }
        }
    }
}
```

## SecurityScheme Reference

| Property | Type | Description |
|----------|------|-------------|
| `type` | `String` | `"http"`, `"apiKey"`, `"oauth2"`, `"openIdConnect"` |
| `scheme` | `String?` | For HTTP: `"bearer"`, `"basic"` |
| `bearerFormat` | `String?` | Format hint: `"JWT"`, etc. |
| `in` | `String?` | For apiKey: `"header"`, `"query"`, `"cookie"` |
| `name` | `String?` | For apiKey: header/query/cookie name |
| `description` | `String?` | Description of this security scheme |
| `flows` | `OAuthFlows?` | OAuth 2.0 flow configuration |
| `openIdConnectUrl` | `String?` | OpenID Connect discovery URL |
