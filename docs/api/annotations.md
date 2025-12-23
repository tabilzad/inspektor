# Annotations

Reference for all InspeKtor annotations.

## @GenerateOpenApi

Enables OpenAPI specification generation for the annotated function.

```kotlin
import io.github.tabilzad.ktor.annotations.GenerateOpenApi
```

### Usage

Apply to your main application module:

```kotlin
@GenerateOpenApi
fun Application.module() {
    routing {
        // Routes here will be documented
    }
}
```

Or to specific route functions:

```kotlin
@GenerateOpenApi
fun Route.apiRoutes() {
    route("/api") {
        // Routes here will be documented
    }
}
```

### Multiple Modules

You can have multiple `@GenerateOpenApi` annotations across your codebase:

```kotlin
// UserModule.kt
@GenerateOpenApi
fun Application.configureUserRoutes() {
    routing { userRoutes() }
}

// ProductModule.kt
@GenerateOpenApi
fun Application.configureProductRoutes() {
    routing { productRoutes() }
}
```

All routes are merged into a single OpenAPI specification.

### Requirements

- Must be applied to a function
- The function should define Ktor routes (directly or via calls to other functions)
- At least one `@GenerateOpenApi` annotation is required for spec generation

---

## @KtorDescription

Adds documentation metadata to an endpoint.

```kotlin
import io.github.tabilzad.ktor.annotations.KtorDescription
```

### Properties

| Property      | Type      | Default | Description                              |
|---------------|-----------|---------|------------------------------------------|
| `summary`     | `String`  | `""`    | Brief one-line description               |
| `description` | `String`  | `""`    | Detailed description (supports Markdown) |
| `operationId` | `String`  | `""`    | Custom operation ID for code generation  |
| `deprecated`  | `Boolean` | `false` | Mark endpoint as deprecated              |

### Usage

Basic usage:

```kotlin
@KtorDescription(summary = "Get user by ID")
get("/users/{id}") {
    // Implementation
}
```

Full usage:

```kotlin
@KtorDescription(
    summary = "Create a new user",
    description = """
        Creates a new user account with the provided information.

        ## Required Fields
        - `name`: User's display name
        - `email`: Valid email address

        ## Notes
        - A welcome email will be sent to the user
        - The user must verify their email within 24 hours
    """,
    operationId = "createUser",
    deprecated = false
)
post("/users") {
    val request = call.receive<CreateUserRequest>()
    // Implementation
}
```

### Deprecated Endpoint

```kotlin
@KtorDescription(
    summary = "Get user (deprecated)",
    description = "Use GET /api/v2/users/{id} instead",
    deprecated = true
)
get("/api/v1/users/{id}") {
    // Old implementation
}
```

### Placement

Apply directly before the HTTP method call:

```kotlin
route("/users") {
    @KtorDescription(summary = "List users")
    get { }

    @KtorDescription(summary = "Create user")
    post { }

    route("/{id}") {
        @KtorDescription(summary = "Get user")
        get { }

        @KtorDescription(summary = "Update user")
        put { }

        @KtorDescription(summary = "Delete user")
        delete { }
    }
}
```

---

## @Tag

Groups endpoints under one or more tags for organization.

```kotlin
import io.github.tabilzad.ktor.annotations.Tag
```

### Properties

| Property | Type            | Description       |
|----------|-----------------|-------------------|
| `value`  | `Array<String>` | List of tag names |

### Usage

Single tag:

```kotlin
@Tag(["Users"])
fun Route.userRoutes() {
    route("/users") {
        get { }  // Tagged: Users
        post { } // Tagged: Users
    }
}
```

Multiple tags:

```kotlin
@Tag(["Users", "Admin"])
fun Route.adminUserRoutes() {
    route("/admin/users") {
        get { }  // Tagged: Users, Admin
    }
}
```

### Inheritance

Tags are inherited by nested routes:

```kotlin
@Tag(["Orders"])
fun Route.orderRoutes() {
    route("/orders") {
        get { }  // Tagged: Orders

        route("/{id}") {
            get { }    // Tagged: Orders
            put { }    // Tagged: Orders
            delete { } // Tagged: Orders
        }
    }
}
```

### Overriding

Override inherited tags:

```kotlin
@Tag(["Orders"])
fun Route.orderRoutes() {
    route("/orders") {
        get { }  // Tagged: Orders

        @Tag(["OrderItems"])
        route("/{id}/items") {
            get { }  // Tagged: OrderItems (not Orders)
        }
    }
}
```

### Placement

Apply to route functions or route blocks:

```kotlin
// On a function
@Tag(["Users"])
fun Route.userRoutes() {
}

// On a route block (inside routing)
@Tag(["Products"])
route("/products") { }
```

---

## Summary Table

| Annotation         | Target             | Required           | Purpose                |
|--------------------|--------------------|--------------------|------------------------|
| `@GenerateOpenApi` | Function           | Yes (at least one) | Enable spec generation |
| `@KtorDescription` | Before HTTP method | No                 | Add documentation      |
| `@Tag`             | Function or route  | No                 | Group endpoints        |

## Complete Example

```kotlin
import io.github.tabilzad.ktor.annotations.*
import io.github.tabilzad.ktor.responds

@GenerateOpenApi
fun Application.module() {
    routing {
        healthRoutes()
        userRoutes()
    }
}

@Tag(["Health"])
fun Route.healthRoutes() {
    @KtorDescription(summary = "Health check endpoint")
    get("/health") {
        responds<HealthStatus>(HttpStatusCode.OK)
        call.respond(HealthStatus("healthy"))
    }
}

@Tag(["Users"])
fun Route.userRoutes() {
    route("/users") {
        @KtorDescription(
            summary = "List all users",
            description = "Returns a paginated list of users"
        )
        get {
            responds<List<User>>(HttpStatusCode.OK)
        }

        @KtorDescription(summary = "Create a new user")
        post {
            responds<User>(HttpStatusCode.Created)
            responds<ErrorResponse>(HttpStatusCode.BadRequest)
            val request = call.receive<CreateUserRequest>()
        }

        route("/{id}") {
            @KtorDescription(summary = "Get user by ID")
            get {
                responds<User>(HttpStatusCode.OK)
                responds<ErrorResponse>(HttpStatusCode.NotFound)
            }

            @KtorDescription(summary = "Update user")
            put {
                responds<User>(HttpStatusCode.OK)
                responds<ErrorResponse>(HttpStatusCode.NotFound)
                val request = call.receive<UpdateUserRequest>()
            }

            @KtorDescription(summary = "Delete user")
            delete {
                respondsNothing(HttpStatusCode.NoContent)
                responds<ErrorResponse>(HttpStatusCode.NotFound)
            }
        }
    }
}
```
