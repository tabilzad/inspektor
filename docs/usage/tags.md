# Tags

Organize your API endpoints into logical groups.

## `@Tag` Annotation

Use the `@Tag` annotation to group related endpoints:

```kotlin
import io.github.tabilzad.ktor.annotations.Tag

@Tag(["Users"])
fun Route.userRoutes() {
    route("/users") {
        get { /* Listed under "Users" tag */ }
        post { /* Listed under "Users" tag */ }
    }
}

@Tag(["Products"])
fun Route.productRoutes() {
    route("/products") {
        get { /* Listed under "Products" tag */ }
        post { /* Listed under "Products" tag */ }
    }
}
```

Generated OpenAPI:

```yaml
paths:
  /users:
    get:
      tags:
        - Users
    post:
      tags:
        - Users
  /products:
    get:
      tags:
        - Products
    post:
      tags:
        - Products
```

## Multiple Tags

An endpoint can belong to multiple tags:

```kotlin
@Tag(["Users", "Admin"])
fun Route.adminUserRoutes() {
    route("/admin/users") {
        get { /* Listed under both "Users" and "Admin" */ }
    }
}
```

## Tag Inheritance

Tags are inherited by nested routes:

```kotlin
@Tag(["Orders"])
fun Route.orderRoutes() {
    route("/orders") {
        get { /* Tagged: Orders */ }
        post { /* Tagged: Orders */ }

        route("/{orderId}") {
            get { /* Tagged: Orders */ }
            put { /* Tagged: Orders */ }
            delete { /* Tagged: Orders */ }

            route("/items") {
                get { /* Tagged: Orders */ }
                post { /* Tagged: Orders */ }
            }
        }
    }
}
```

## Overriding Tags

You can override inherited tags on specific endpoints:

```kotlin
@Tag(["Orders"])
fun Route.orderRoutes() {
    route("/orders") {
        get { /* Tagged: Orders */ }

        @Tag(["Order Items"])
        route("/{orderId}/items") {
            get { /* Tagged: Order Items (overrides Orders) */ }
        }
    }
}
```

## Tag Naming Conventions

### Good Tag Names

- **Resource-based**: `Users`, `Products`, `Orders`
- **Feature-based**: `Authentication`, `Notifications`, `Search`
- **Version-based**: `v1`, `v2` (use sparingly)
- **Access-level**: `Public`, `Admin`, `Internal`

### Examples

```kotlin
// Resource-based tags
@Tag(["Users"])
fun Route.userRoutes() { }

@Tag(["Products"])
fun Route.productRoutes() { }

@Tag(["Orders"])
fun Route.orderRoutes() { }

// Feature-based tags
@Tag(["Authentication"])
fun Route.authRoutes() { }

@Tag(["Search"])
fun Route.searchRoutes() { }

// Combined approach
@Tag(["Users", "Admin"])
fun Route.adminUserRoutes() { }
```

## Organizing a Large API

### By Resource

```kotlin
@GenerateOpenApi
fun Application.module() {
    routing {
        // Each resource gets its own tag
        userRoutes()      // @Tag(["Users"])
        productRoutes()   // @Tag(["Products"])
        orderRoutes()     // @Tag(["Orders"])
        reviewRoutes()    // @Tag(["Reviews"])
    }
}
```

### By Feature Area

```kotlin
@GenerateOpenApi
fun Application.module() {
    routing {
        // Public endpoints
        publicRoutes()    // @Tag(["Public"])

        authenticate {
            // Customer-facing
            customerRoutes()  // @Tag(["Customer"])

            // Admin-only
            adminRoutes()     // @Tag(["Admin"])
        }
    }
}
```

### By API Version

```kotlin
@GenerateOpenApi
fun Application.module() {
    routing {
        route("/api/v1") {
            @Tag(["v1 - Users"])
            route("/users") { }

            @Tag(["v1 - Products"])
            route("/products") { }
        }

        route("/api/v2") {
            @Tag(["v2 - Users"])
            route("/users") { }

            @Tag(["v2 - Products"])
            route("/products") { }
        }
    }
}
```

## Tags in Swagger UI

Tags control how endpoints are grouped in Swagger UI:

```
├── Users
│   ├── GET /users
│   ├── POST /users
│   ├── GET /users/{id}
│   ├── PUT /users/{id}
│   └── DELETE /users/{id}
├── Products
│   ├── GET /products
│   ├── POST /products
│   └── ...
└── Orders
    ├── GET /orders
    └── ...
```

## Combining with Descriptions

Use tags alongside descriptions for clear documentation:

```kotlin
@Tag(["Users"])
fun Route.userRoutes() {
    route("/users") {
        @KtorDescription(
            summary = "List all users",
            description = "Returns a paginated list of all users"
        )
        get {
            responds<List<User>>(HttpStatusCode.OK)
        }

        @KtorDescription(
            summary = "Create a new user",
            description = "Creates a user account and sends welcome email"
        )
        post {
            responds<User>(HttpStatusCode.Created)
            val request = call.receive<CreateUserRequest>()
        }
    }
}
```

## Best Practices

1. **Use consistent naming** - Pick a convention and stick to it
2. **Keep tags high-level** - Don't create too many tags
3. **Group logically** - Tags should represent logical API areas
4. **Consider your audience** - Name tags from the API consumer's perspective
5. **Avoid duplication** - Each endpoint should typically have 1-2 tags

### Recommended Tag Count

| API Size | Recommended Tags |
|----------|------------------|
| Small (< 20 endpoints) | 3-5 tags |
| Medium (20-50 endpoints) | 5-10 tags |
| Large (50+ endpoints) | 10-15 tags |

Too many tags make navigation difficult. Too few provide inadequate organization.
