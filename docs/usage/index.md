# Usage Guide

Learn how to document your Ktor API endpoints effectively with InspeKtor.

## Core Concepts

InspeKtor analyzes your Ktor routing DSL at compile time and automatically generates OpenAPI documentation. The key
concepts are:

| Concept                             | Purpose                             |
|-------------------------------------|-------------------------------------|
| [Endpoints](endpoints.md)           | Route definitions and HTTP methods  |
| [Request Bodies](request-bodies.md) | Input data schemas                  |
| [Responses](responses.md)           | Output data and status codes        |
| [Descriptions](descriptions.md)     | Summaries and detailed descriptions |
| [Tags](tags.md)                     | Grouping and organization           |
| [Parameters](parameters.md)         | Path, query, and header parameters  |

## The @GenerateOpenApi Annotation

The `@GenerateOpenApi` annotation is required on at least one function to enable documentation generation:

```kotlin
import io.github.tabilzad.ktor.annotations.GenerateOpenApi

@GenerateOpenApi
fun Application.module() {
    routing {
        // All routes defined here will be documented
    }
}
```

You can also annotate individual route functions:

```kotlin
@GenerateOpenApi
fun Route.apiRoutes() {
    route("/api") {
        // Routes here will be documented
    }
}
```

## Automatic Detection

InspeKtor automatically detects:

- **Route paths** from `route()`, `get()`, `post()`, etc.
- **Request bodies** from `call.receive<T>()` calls
- **Path parameters** from `{param}` syntax
- **Query parameters** from `call.request.queryParameters`
- **Schema types** from your data classes

## Documentation Annotations

Enhance your documentation with these annotations:

```kotlin
import io.github.tabilzad.ktor.annotations.*
import io.github.tabilzad.ktor.responds

@Tag(["Users"])
fun Route.userRoutes() {
    route("/users") {

        @KtorDescription(
            summary = "List all users",
            description = "Returns a paginated list of users"
        )
        get {
            responds<List<User>>(HttpStatusCode.OK)
            // Implementation
        }

        @KtorDescription(summary = "Create user")
        post {
            responds<User>(HttpStatusCode.Created, description = "User created")
            responds<ErrorResponse>(HttpStatusCode.BadRequest)

            val request = call.receive<CreateUserRequest>()
            // Implementation
        }
    }
}
```

## Workflow

1. **Define your routes** using Ktor's routing DSL
2. **Add `@GenerateOpenApi`** to your module function
3. **Add annotations** for enhanced documentation
4. **Build your project** - spec is generated automatically
5. **View the spec** at `build/resources/main/openapi/openapi.yaml`

## Quick Examples

### Simple GET endpoint

```kotlin
@KtorDescription(summary = "Get server status")
get("/health") {
    responds<HealthStatus>(HttpStatusCode.OK)
    call.respond(HealthStatus(status = "healthy"))
}
```

### POST with request body

```kotlin
@KtorDescription(summary = "Create a new product")
post("/products") {
    responds<Product>(HttpStatusCode.Created)
    responds<ErrorResponse>(HttpStatusCode.BadRequest)

    val request = call.receive<CreateProductRequest>()
    // Create product...
}
```

### Path parameters

```kotlin
route("/users/{userId}") {
    @KtorDescription(summary = "Get user by ID")
    get {
        responds<User>(HttpStatusCode.OK)
        responds<ErrorResponse>(HttpStatusCode.NotFound)

        val userId = call.parameters["userId"]
        // Fetch user...
    }
}
```

### Nested routes with tags

```kotlin
@Tag(["Orders"])
fun Route.orderRoutes() {
    route("/orders") {
        get { /* List orders */ }
        post { /* Create order */ }

        route("/{orderId}") {
            get { /* Get order */ }
            put { /* Update order */ }
            delete { /* Delete order */ }
        }
    }
}
```
