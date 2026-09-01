# Descriptions

Add summaries and detailed descriptions to your API documentation.

## @KtorDescription Annotation

The primary way to add descriptions to endpoints:

```kotlin
import io.github.tabilzad.ktor.annotations.KtorDescription

@KtorDescription(
    summary = "Get user by ID",
    description = "Retrieves a user by their unique identifier. Returns 404 if the user does not exist."
)
get("/users/{id}") {
    // Implementation
}
```

Generated OpenAPI:

```yaml
/users/{id}:
  get:
    summary: "Get user by ID"
    description: "Retrieves a user by their unique identifier. Returns 404 if the user does not exist."
```

## Summary vs Description

| Field         | Purpose                    | Length                         |
|---------------|----------------------------|--------------------------------|
| `summary`     | Brief one-line description | Short (< 120 chars)            |
| `description` | Detailed explanation       | Can be long, supports markdown |

```kotlin
@KtorDescription(
    summary = "Create a new order",
    description = """
        Creates a new order with the specified items.

        ## Process
        1. Validates all product IDs exist
        2. Checks inventory availability
        3. Calculates pricing and taxes
        4. Creates the order record
        5. Sends confirmation email

        ## Notes
        - Orders cannot be modified after creation
        - Use DELETE to cancel within 24 hours
    """
)
post("/orders") {
    // Implementation
}
```

## Markdown Support

Descriptions support Markdown formatting:

```kotlin
@KtorDescription(
    summary = "Search products",
    description = """
        Search for products using various filters.

        **Supported filters:**
        - `name` - Product name (partial match)
        - `category` - Category ID
        - `minPrice` / `maxPrice` - Price range

        **Example:**
        ```
        GET /products?name=laptop&minPrice=500
        ```

        See [filtering guide](/docs/filtering) for more details.
    """
)
get("/products") {
    // Implementation
}
```

## Operation ID

Customize the operation ID (used for code generation):

```kotlin
@KtorDescription(
    summary = "List users",
    operationId = "listAllUsers"  // Custom operation ID
)
get("/users") {
    // Implementation
}
```

If not specified, InspeKtor generates an operation ID from the method and path.

## Deprecated Endpoints

Deprecation comes from Kotlin's own `@Deprecated` annotation on the route function declaring
the endpoints (it cannot be placed on a `get(...)`/`post(...)` expression directly):

```kotlin
@Deprecated("Use GET /api/v2/users/{id} instead")
fun Route.legacyUserRoutes() {
    @KtorDescription(summary = "Get user (deprecated)")
    get("/api/v1/users/{id}") {
        // Old implementation
    }
}
```

Generated — the deprecation message is appended to the operation description:

```yaml
/api/v1/users/{id}:
  get:
    summary: "Get user (deprecated)"
    description: "Deprecated: Use GET /api/v2/users/{id} instead"
    deprecated: true
```

## Deprecated Schemas and Fields

`@Deprecated` on data classes and their properties is reflected in the generated schemas.
It works for both classes defined in the compiled module and classes coming from other
modules or libraries (the annotation survives compilation, unlike KDoc):

```kotlin
@Deprecated("Use CurrentPayload instead")
data class LegacyPayload(val id: String)

data class CurrentPayload(
    /** The legacy identifier. */
    @Deprecated("Use id instead")
    val legacyId: String?,
    val id: String
)
```

Generated:

```yaml
LegacyPayload:
  type: object
  description: "Deprecated: Use CurrentPayload instead"
  deprecated: true
  properties:
    id:
      type: string
CurrentPayload:
  type: object
  properties:
    legacyId:
      type: string
      description: "The legacy identifier.\n\nDeprecated: Use id instead"
      deprecated: true
    id:
      type: string
```

## KDoc for Schema Descriptions

Use KDoc comments to document your data classes:

```kotlin
/**
 * Represents a user in the system
 */
data class User(
    /** Unique user identifier */
    val id: Long,

    /** User's display name */
    val name: String,

    /** User's email address (unique) */
    val email: String,

    /** Account creation timestamp */
    val createdAt: Instant,

    /** Whether the user has verified their email */
    val isVerified: Boolean
)
```

Generated schema:

```yaml
User:
  type: object
  description: "Represents a user in the system"
  properties:
    id:
      type: integer
      format: int64
      description: "Unique user identifier"
    name:
      type: string
      description: "User's display name"
    email:
      type: string
      description: "User's email address (unique)"
    createdAt:
      type: string
      format: date-time
      description: "Account creation timestamp"
    isVerified:
      type: boolean
      description: "Whether the user has verified their email"
```

Enable KDoc extraction:

```kotlin
swagger {
    documentation {
        useKDocsForDescriptions = true // default
    }
}
```

## Enum Descriptions

Document enum values:

```kotlin
/**
 * Order status values
 */
enum class OrderStatus {
    /** Order received, awaiting processing */
    PENDING,

    /** Order is being prepared */
    PROCESSING,

    /** Order has been shipped */
    SHIPPED,

    /** Order delivered to customer */
    DELIVERED,

    /** Order was cancelled */
    CANCELLED
}
```

## Route-Level Descriptions

Apply descriptions to groups of routes:

```kotlin
@Tag(["Users"])
fun Route.userRoutes() {
    route("/users") {
        @KtorDescription(summary = "List all users")
        get { /* ... */ }

        @KtorDescription(summary = "Create user")
        post { /* ... */ }

        route("/{id}") {
            @KtorDescription(summary = "Get user by ID")
            get { /* ... */ }

            @KtorDescription(summary = "Update user")
            put { /* ... */ }

            @KtorDescription(summary = "Delete user")
            delete { /* ... */ }
        }
    }
}
```

## API-Level Description

Set the overall API description in your build configuration:

```kotlin title="build.gradle.kts"
swagger {
    documentation {
        info {
            title = "My API"
            description = """
                # Welcome to My API

                This API provides access to our platform's resources.

                ## Authentication
                All endpoints require a Bearer token in the Authorization header.

                ## Rate Limiting
                - 100 requests per minute for free tier
                - 1000 requests per minute for premium tier

                ## Support
                Contact api-support@example.com for assistance.
            """
            version = "1.0.0"
        }
    }
}
```

## Best Practices

### Do

- ✅ Write clear, concise summaries
- ✅ Include important details in descriptions
- ✅ Document error conditions
- ✅ Use consistent terminology
- ✅ Add examples where helpful

### Don't

- ❌ Repeat the HTTP method in the summary ("GET user" → "Get user")
- ❌ Leave summaries empty
- ❌ Write overly technical descriptions for public APIs
- ❌ Include implementation details

### Examples of Good Summaries

```kotlin
// Good
@KtorDescription(summary = "List all products")
@KtorDescription(summary = "Create a new order")
@KtorDescription(summary = "Get user by ID")
@KtorDescription(summary = "Update product inventory")
@KtorDescription(summary = "Delete customer account")

// Avoid
@KtorDescription(summary = "GET /products endpoint")
@KtorDescription(summary = "This endpoint creates an order")
@KtorDescription(summary = "Returns user")
```

### Examples of Good Descriptions

```kotlin
@KtorDescription(
    summary = "Search products",
    description = """
        Search for products by name, category, or price range.

        Results are paginated with a default page size of 20.
        Use `page` and `size` query parameters to navigate results.

        Returns an empty list if no products match the criteria.
    """
)
```
