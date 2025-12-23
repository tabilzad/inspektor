# Endpoints

Learn how InspeKtor documents your Ktor route definitions.

## HTTP Methods

InspeKtor automatically detects all standard HTTP methods:

```kotlin
route("/resource") {
    get { /* GET /resource */ }
    post { /* POST /resource */ }
    put { /* PUT /resource */ }
    patch { /* PATCH /resource */ }
    delete { /* DELETE /resource */ }
    head { /* HEAD /resource */ }
    options { /* OPTIONS /resource */ }
}
```

## Route Definitions

### Simple Routes

```kotlin
get("/users") {
    // Documented as GET /users
}

post("/users") {
    // Documented as POST /users
}
```

### Nested Routes

```kotlin
route("/api") {
    route("/v1") {
        route("/users") {
            get {
                // Documented as GET /api/v1/users
            }
        }
    }
}
```

### Path Parameters

Path parameters are automatically detected from the `{param}` syntax:

```kotlin
route("/users/{userId}") {
    get {
        // Documented with path parameter 'userId'
        val userId = call.parameters["userId"]
    }

    route("/posts/{postId}") {
        get {
            // Documented with both 'userId' and 'postId' parameters
        }
    }
}
```

Generated OpenAPI:

```yaml
/users/{userId}:
  get:
    parameters:
      - name: userId
        in: path
        required: true
        schema:
          type: string

/users/{userId}/posts/{postId}:
  get:
    parameters:
      - name: userId
        in: path
        required: true
        schema:
          type: string
      - name: postId
        in: path
        required: true
        schema:
          type: string
```

## Organizing Routes

### Separate Route Functions

Split your routes into separate functions for better organization:

```kotlin
@GenerateOpenApi
fun Application.module() {
    routing {
        userRoutes()
        productRoutes()
        orderRoutes()
    }
}

fun Route.userRoutes() {
    route("/users") {
        get { /* ... */ }
        post { /* ... */ }
    }
}

fun Route.productRoutes() {
    route("/products") {
        get { /* ... */ }
        post { /* ... */ }
    }
}
```

### Route Extensions

Use extension functions for cleaner code:

```kotlin
fun Route.crudRoutes(path: String, handler: CrudHandler) {
    route(path) {
        get { handler.list(call) }
        post { handler.create(call) }
        route("/{id}") {
            get { handler.get(call) }
            put { handler.update(call) }
            delete { handler.delete(call) }
        }
    }
}
```

## Multiple API Versions

Document multiple API versions:

```kotlin
@GenerateOpenApi
fun Application.module() {
    routing {
        route("/api/v1") {
            @Tag(["v1"])
            route("/users") {
                get { /* v1 implementation */ }
            }
        }

        route("/api/v2") {
            @Tag(["v2"])
            route("/users") {
                get { /* v2 implementation */ }
            }
        }
    }
}
```

## Base Path

All routes are documented relative to the server URL configured in your build.gradle.kts:

```kotlin
swagger {
    documentation {
        servers = listOf(
            "https://api.example.com",
            "http://localhost:8080"
        )
    }
}
```

## Authenticated Routes

Routes under Ktor's `authenticate` block work the same way:

```kotlin
routing {
    // Public routes
    get("/health") { /* ... */ }

    authenticate("auth-jwt") {
        // Protected routes - document security in build.gradle.kts
        route("/users") {
            get { /* ... */ }
        }
    }
}
```

## Deprecated Endpoints

Mark endpoints as deprecated:

```kotlin
@KtorDescription(
    summary = "Get user (deprecated)",
    description = "Use /api/v2/users instead",
    deprecated = true
)
get("/api/v1/users/{id}") {
    // Old implementation
}
```

Generated:

```yaml
/api/v1/users/{id}:
  get:
    deprecated: true
    summary: "Get user (deprecated)"
    description: "Use /api/v2/users instead"
```

## Wildcard Routes

Wildcard routes are documented with the wildcard syntax:

```kotlin
route("/files/{path...}") {
    get {
        // Documented as /files/{path}
        val path = call.parameters.getAll("path")
    }
}
```

## Static Content

Static content routes are generally excluded from API documentation. If you need to document file serving endpoints, use
explicit routes:

```kotlin
@KtorDescription(summary = "Download file")
get("/downloads/{filename}") {
    responds<ByteArray>(HttpStatusCode.OK, contentType = "application/octet-stream")
    // Serve file
}
```
