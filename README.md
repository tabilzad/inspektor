<p align="center">
  <img width="200" height="200" src="https://github.com/user-attachments/assets/da646150-24f3-43af-8b8f-188848f284a5" />
</p>

<h1 align="center">InspeKtor</h1>

<p align="center">
  <strong>OpenAPI (Swagger) specification generator for Ktor</strong>
</p>

<p align="center">
  <a href="https://github.com/tabilzad/inspektor/actions/workflows/gradle-publish.yml">
    <img src="https://github.com/tabilzad/inspektor/actions/workflows/gradle-publish.yml/badge.svg" alt="Build Status"/>
  </a>
  <a href="https://central.sonatype.com/artifact/io.github.tabilzad.inspektor/ktor-docs-plugin">
    <img src="https://img.shields.io/maven-central/v/io.github.tabilzad.inspektor/ktor-docs-plugin?color=blue" alt="Maven Central"/>
  </a>
  <a href="https://kotlinlang.org">
    <img src="https://img.shields.io/badge/kotlin-2.3.0-blue.svg?logo=kotlin" alt="Kotlin"/>
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-Apache%202.0-green.svg" alt="License"/>
  </a>
</p>

<p align="center">
  <a href="#quick-start">Quick Start</a> •
  <a href="#features">Features</a> •
  <a href="#configuration">Configuration</a> •
  <a href="#usage-guide">Usage Guide</a> •
  <a href="#examples">Examples</a>
</p>

---

## What is InspeKtor?

InspeKtor is a **Kotlin compiler plugin** that automatically generates OpenAPI (Swagger) specifications from your Ktor
server code at **build time**. No runtime overhead, no special DSL wrappers, no code modifications required.

Simply annotate your route definitions with `@GenerateOpenApi` and get a complete `openapi.yaml` or `openapi.json`
specification.

<p align="center">
  <img src="https://github.com/tabilzad/inspektor/assets/16094286/6d0b0a6a-5925-4f52-ad23-11b1c44b43a1" alt="Sample OpenAPI Specification" width="700"/>
</p>

### Why InspeKtor?

|                    | Traditional Approach                        | InspeKtor                              |
|--------------------|---------------------------------------------|----------------------------------------|
| **Setup**          | Manual spec writing or complex DSL wrappers | Single annotation                      |
| **Maintenance**    | Spec drifts from code over time             | Always in sync - generated from source |
| **Performance**    | Runtime overhead for spec generation        | Zero runtime cost - build time only    |
| **Learning Curve** | Learn new DSL or spec format                | Use your existing Ktor code            |

---

## Quick Start

### 1. Add the Plugin

```kotlin
// build.gradle.kts
plugins {
    id("io.github.tabilzad.inspektor") version "0.10.0-alpha"
}
```

### 2. Annotate Your Routes

```kotlin
@GenerateOpenApi
fun Application.module() {
    routing {
        get("/hello") {
            call.respondText("Hello, World!")
        }

        post("/users") {
            val user = call.receive<CreateUserRequest>()
            call.respond(HttpStatusCode.Created, user)
        }
    }
}

data class CreateUserRequest(val name: String, val email: String)
```

### 3. Build Your Project

```bash
./gradlew build
```

Your OpenAPI specification is generated at `build/resources/main/openapi/openapi.yaml`

> **Want a complete example?** Check out the [Sample Project](https://github.com/tabilzad/ktor-inspektor-example)

---

## Features

| Feature                       | Type          | Description                                    |
|-------------------------------|---------------|------------------------------------------------|
| **Path & Endpoint Detection** | Automatic     | Extracts all routes from annotated functions   |
| **Ktor Resources Support**    | Automatic     | Full support for type-safe routing             |
| **Request Body Schemas**      | Automatic     | Generates schemas from `call.receive<T>()`     |
| **Response Schemas**          | Explicit      | Define with `responds<T>()` or `@KtorResponds` |
| **Descriptions**              | Explicit      | Add summaries via `@KtorDescription` or KDocs  |
| **Tags**                      | Explicit      | Organize endpoints with `@Tag`                 |
| **Security Schemes**          | Configuration | JWT, API Key, OAuth2, etc.                     |
| **Sealed Classes**            | Automatic     | `oneOf` with discriminators                    |
| **Value Classes**             | Automatic     | Unwrapped to underlying type                   |
| **Generic Types**             | Automatic     | Full support for parameterized types           |

---

## Configuration

### Basic Configuration

```kotlin
swagger {
    documentation {
        info {
            title = "My API"
            description = "API documentation for my Ktor server"
            version = "1.0.0"
            contact {
                name = "API Support"
                url = "https://example.com/support"
            }
        }
        servers = listOf("https://api.example.com", "http://localhost:8080")
    }

    pluginOptions {
        format = "yaml"  // or "json"
    }
}
```

### Documentation Options

| Option                                      | Default                              | Description                              |
|---------------------------------------------|--------------------------------------|------------------------------------------|
| `info.title`                                | `"Open API Specification"`           | API specification title                  |
| `info.description`                          | `"Generated using Ktor Docs Plugin"` | API description                          |
| `info.version`                              | `"1.0.0"`                            | API version                              |
| `generateRequestSchemas`                    | `true`                               | Auto-resolve request body schemas        |
| `hideTransientFields`                       | `true`                               | Omit `@Transient` fields from schemas    |
| `hidePrivateAndInternalFields`              | `true`                               | Omit private/internal fields             |
| `deriveFieldRequirementFromTypeNullability` | `true`                               | Nullable = optional, non-null = required |
| `useKDocsForDescriptions`                   | `true`                               | Extract descriptions from KDoc comments  |
| `servers`                                   | `[]`                                 | List of server URLs                      |

### Plugin Options

| Option             | Default                         | Description                                                      |
|--------------------|---------------------------------|------------------------------------------------------------------|
| `enabled`          | `true`                          | Enable/disable the plugin                                        |
| `saveInBuild`      | `true`                          | Save spec in `build/` directory                                  |
| `format`           | `"yaml"`                        | Output format: `yaml` or `json`                                  |
| `filePath`         | `build/resources/main/openapi/` | Custom output path                                               |
| `regenerationMode` | `"strict"`                      | Incremental build behavior ([details](#incremental-compilation)) |

---

## Usage Guide

### Defining Endpoints

Annotate route functions to generate their specifications:

```kotlin
@GenerateOpenApi
fun Route.usersApi() {
    route("/api/v1/users") {
        get { /* List users */ }
        post { /* Create user */ }

        route("/{id}") {
            get { /* Get user by ID */ }
            put { /* Update user */ }
            delete { /* Delete user */ }
        }
    }
}
```

You can also annotate entire application modules:

```kotlin
@GenerateOpenApi
fun Application.apiModule() {
    routing {
        usersApi()      // All routes are included
        productsApi()   // Even nested route functions
        ordersApi()
    }
}
```

### Adding Descriptions

Use `@KtorDescription` for endpoint documentation:

```kotlin
@GenerateOpenApi
fun Route.ordersApi() {
    route("/orders") {
        @KtorDescription(
            summary = "Create Order",
            description = "Creates a new order with the provided items"
        )
        post {
            val order = call.receive<CreateOrderRequest>()
            // ...
        }
    }
}
```

Use `@KtorSchema` and `@KtorField` for schema documentation:

```kotlin
@KtorSchema("Request payload for creating a new order")
data class CreateOrderRequest(
    @KtorField("List of items to include in the order")
    val items: List<OrderItem>,

    @KtorField("Optional discount code")
    val discountCode: String? = null
)
```

Or simply use KDoc comments:

```kotlin
/**
 * Request payload for creating a new order
 * @property items List of items to include in the order
 * @property discountCode Optional discount code
 */
data class CreateOrderRequest(
    val items: List<OrderItem>,
    val discountCode: String? = null
)
```

### Defining Responses

**Option 1: Inline DSL (Recommended)**

```kotlin
post("/orders") {
    responds<Order>(HttpStatusCode.Created, description = "Order created successfully")
    responds<ErrorResponse>(HttpStatusCode.BadRequest, description = "Invalid request")
    respondsNothing(HttpStatusCode.Unauthorized)

    // Your handler code...
}
```

**Option 2: Annotation**

```kotlin
@KtorResponds(
    [
        ResponseEntry("201", Order::class, description = "Order created"),
        ResponseEntry("400", ErrorResponse::class, description = "Invalid request"),
        ResponseEntry("401", Nothing::class)
    ]
)
post("/orders") { /* ... */ }
```

### Tagging Endpoints

Group endpoints by applying tags:

```kotlin
@Tag(["Users"])
fun Route.usersApi() {
    get("/users") { /* tagged as "Users" */ }
    post("/users") { /* tagged as "Users" */ }
}

// Or on individual endpoints:
@GenerateOpenApi
fun Route.api() {
    @Tag(["Users"])
    get("/users") { /* ... */ }

    @Tag(["Products"])
    get("/products") { /* ... */ }
}
```

### Security Configuration

Define security schemes in your Gradle configuration:

```kotlin
swagger {
    documentation {
        security {
            schemes {
                "bearerAuth" to SecurityScheme(
                    type = "http",
                    scheme = "bearer",
                    bearerFormat = "JWT"
                )
                "apiKey" to SecurityScheme(
                    type = "apiKey",
                    `in` = "header",
                    name = "X-API-Key"
                )
            }
            scopes {
                or { +"bearerAuth" }
                or { +"apiKey" }
            }
        }
    }
}
```

### Type Overrides

Customize how specific types appear in the schema:

```kotlin
swagger {
    documentation {
        serialOverrides {
            typeOverride("java.time.Instant") {
                serializedAs = "string"
                format = "date-time"
                description = "ISO 8601 timestamp"
            }
            typeOverride("java.util.UUID") {
                serializedAs = "string"
                format = "uuid"
            }
        }
    }
}
```

### Polymorphic Types

Sealed classes are automatically handled with `oneOf`:

```kotlin
@JsonClassDiscriminator("type")
sealed class PaymentMethod {
    data class CreditCard(val cardNumber: String, val cvv: String) : PaymentMethod()
    data class BankTransfer(val iban: String) : PaymentMethod()
}
```

Configure the default discriminator property:

```kotlin
swagger {
    documentation {
        polymorphicDiscriminator = "type"  // default
    }
}
```

---

## Incremental Compilation

The plugin supports three regeneration modes to balance build speed and specification completeness:

| Mode     | Build Speed | Completeness     | Recommended For   |
|----------|-------------|------------------|-------------------|
| `strict` | Slower      | Always complete  | CI/CD, releases   |
| `safe`   | Balanced    | Usually complete | Local development |
| `fast`   | Fastest     | May be partial   | Rapid prototyping |

```kotlin
swagger {
    pluginOptions {
        regenerationMode = "strict"  // default
    }
}
```

**`strict`** (Default): Regenerates the full spec on every build. Best for CI/CD pipelines.

**`safe`**: Regenerates when files containing `@GenerateOpenApi` change. Good balance for development.

**`fast`**: Trusts Kotlin's incremental compilation. Fastest, but may produce incomplete specs.

---

## Compatibility

| Plugin Version | Kotlin Version |
|----------------|----------------|
| 0.10.0-alpha   | 2.3.0          |
| 0.8.8-alpha    | 2.2.20, 2.2.21 |
| 0.8.7-alpha    | 2.2.20         |
| 0.8.4-alpha    | 2.2.0          |
| 0.8.0-alpha    | 2.1.20         |
| 0.7.0-alpha    | 2.1.0          |
| 0.6.4-alpha    | 2.0.20         |
| 0.6.0-alpha    | 2.0            |

---

## Roadmap

- [ ] Automatic response type inference from handler code
- [ ] Auto-tagging based on module/route function names
- [ ] Tag descriptions
- [ ] OpenAPI 3.1 full support

---

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to
discuss what you would like to change.

## Support

- [GitHub Issues](https://github.com/tabilzad/inspektor/issues) - Bug reports and feature requests
- [Sample Project](https://github.com/tabilzad/ktor-inspektor-example) - Complete working example

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
