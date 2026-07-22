# Parameters

Document path, query, and header parameters in your API.

## Path Parameters

Path parameters are automatically detected from the `{param}` syntax:

```kotlin
route("/users/{userId}") {
    get {
        val userId = call.parameters["userId"]
        // Parameter 'userId' is automatically documented
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
```

### Multiple Path Parameters

```kotlin
route("/users/{userId}/posts/{postId}") {
    get {
        val userId = call.parameters["userId"]
        val postId = call.parameters["postId"]
    }
}
```

Generated:

```yaml
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

### Nested Routes with Parameters

```kotlin
route("/organizations/{orgId}") {
    route("/teams/{teamId}") {
        route("/members/{memberId}") {
            get {
                // All three parameters are documented
            }
        }
    }
}
```

## Query Parameters

Query parameters are detected from `call.request.queryParameters`:

```kotlin
get("/users") {
    val page = call.request.queryParameters["page"]
    val size = call.request.queryParameters["size"]
    val sort = call.request.queryParameters["sort"]
}
```

Generated:

```yaml
/users:
  get:
    parameters:
      - name: page
        in: query
        schema:
          type: string
      - name: size
        in: query
        schema:
          type: string
      - name: sort
        in: query
        schema:
          type: string
```

### Multiple Values

For parameters that accept multiple values:

```kotlin
get("/products") {
    val categories = call.request.queryParameters.getAll("category")
    // ?category=electronics&category=clothing
}
```

## Header Parameters

Header parameters are detected from `call.request.headers` indexing, the
`call.request.header(...)` accessor, and Ktor's typed header accessors:

```kotlin
get("/users") {
    val apiVersion = call.request.headers["X-API-Version"]
    val requestId = call.request.header("X-Request-ID")
    val agent = call.request.userAgent() // documented as User-Agent
}
```

Generated:

```yaml
/users:
  get:
    parameters:
      - name: X-API-Version
        in: header
        schema:
          type: string
      - name: X-Request-ID
        in: header
        schema:
          type: string
      - name: User-Agent
        in: header
        schema:
          type: string
```

Header names can be string literals, references to constants (including `HttpHeaders.*`),
or string templates combining both.

The typed accessors `userAgent()`, `acceptLanguage()`, `acceptCharset()`, `acceptEncoding()`,
`cacheControl()` and `ranges()` are resolved to their well-known header names automatically.

!!! note "Ignored header names"
    Header parameters named `Accept`, `Content-Type` or `Authorization` are never generated —
    the OpenAPI specification requires tools to ignore them because they are described by the
    operation's `content` and `security` definitions instead.

## Parameter Descriptions

### KDoc on the local variable

Attach a KDoc comment to the `val` a header or query value is assigned to, and it becomes the
parameter's `description` for that endpoint:

```kotlin
get("/users") {
    /** The API key issued to the client. */
    val apiKey = call.request.headers["X-Api-Key"]

    /** Zero-based page index. */
    val page = call.request.queryParameters["page"]
}
```

Generated:

```yaml
/users:
  get:
    parameters:
      - name: X-Api-Key
        in: header
        description: "The API key issued to the client."
        schema:
          type: string
      - name: page
        in: query
        description: "Zero-based page index."
        schema:
          type: string
```

### KDoc on a shared name constant

When the parameter name is a reference to a documented constant, the constant's KDoc is used
everywhere the constant is referenced — handy for headers shared across many endpoints:

```kotlin
object ApiHeaders {
    /** Identifies the tenant the request is scoped to. */
    const val TENANT_ID = "X-Tenant-Id"
}

get("/orders") {
    val tenant = call.request.headers[ApiHeaders.TENANT_ID]
}

get("/invoices") {
    val tenant = call.request.header(ApiHeaders.TENANT_ID)
}
```

Both endpoints document `X-Tenant-Id` with "Identifies the tenant the request is scoped to."

A KDoc on the local `val` takes precedence over the constant's KDoc, so an individual endpoint
can override the shared description. If the same parameter is read in several statements, the
documented access wins.

Both conventions are controlled by the existing `useKDocsForDescriptions` option (enabled by
default). Descriptions are not derived for names built from string templates or concatenation,
since a single piece's KDoc would not describe the full name.

## Declaring Headers with @KtorHeaders

Some headers are consumed by middleware, interceptors, or shared controller plumbing rather
than read inside the route handler — automatic inference cannot see those. Declare them with
`@KtorHeaders`. Placement determines scope:

```kotlin
// On a route module function: applies to every endpoint the module defines
@GenerateOpenApi
@KtorHeaders([HeaderParam("X-Client-Id", "Identity of the calling client", required = true)])
fun Route.ordersApi() {
    routing {
        // On a route: applies to every endpoint nested under it
        @KtorHeaders([HeaderParam("X-Admin-Token", "Admin access token", required = true)])
        route("/admin") {
            get("/users") { /* ... */ }
            delete("/users") { /* ... */ }
        }

        // On an endpoint: applies to that operation only
        @KtorHeaders([HeaderParam("X-Feature-Flag", "Enables experimental behavior")])
        get("/orders") { /* ... */ }
    }
}
```

When the same header is both declared and inferred (or declared at several levels), duplicates
are merged by name: the most specific description wins (endpoint over route over module), and
the header is required if any declaration marks it required.

## Common Headers for All Endpoints

Headers that apply to *every* operation — e.g. extracted globally via
`call.request.headers.toMap()` in an interceptor — are best declared once in the Gradle
configuration instead of annotating every route:

```kotlin
swagger {
    documentation {
        commonHeaders {
            header("X-Request-Id", description = "Correlation id propagated across services")
            header("X-Tenant-Id", description = "Tenant the request is scoped to", required = true)
        }
    }
}
```

Endpoint- and route-level declarations of the same header take precedence over `commonHeaders`
for the description. See the [Gradle DSL reference](../api/gradle-dsl.md#commonheaders) for
details.

### Operation-level descriptions

You can also describe parameters as part of the endpoint text using KtorDescription:

```kotlin
@KtorDescription(
    summary = "Search users",
    description = """
        Search for users with pagination.

        **Query Parameters:**
        - `q` - Search query (searches name and email)
        - `page` - Page number (default: 1)
        - `size` - Page size (default: 20, max: 100)
        - `sort` - Sort field (name, email, createdAt)
        - `order` - Sort order (asc, desc)
    """
)
get("/users/search") {
    val q = call.request.queryParameters["q"]
    val page = call.request.queryParameters["page"]
    val size = call.request.queryParameters["size"]
    val sort = call.request.queryParameters["sort"]
    val order = call.request.queryParameters["order"]
}
```

## Common Parameter Patterns

### Pagination

```kotlin
get("/items") {
    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
    val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
    val offset = call.request.queryParameters["offset"]?.toIntOrNull()
    val limit = call.request.queryParameters["limit"]?.toIntOrNull()
}
```

### Filtering

```kotlin
get("/products") {
    val category = call.request.queryParameters["category"]
    val minPrice = call.request.queryParameters["minPrice"]
    val maxPrice = call.request.queryParameters["maxPrice"]
    val inStock = call.request.queryParameters["inStock"]
}
```

### Sorting

```kotlin
get("/users") {
    val sortBy = call.request.queryParameters["sortBy"] // field name
    val sortOrder = call.request.queryParameters["sortOrder"] // asc/desc
}
```

### Date Ranges

```kotlin
get("/events") {
    val startDate = call.request.queryParameters["startDate"]
    val endDate = call.request.queryParameters["endDate"]
}
```

### Search

```kotlin
get("/search") {
    val query = call.request.queryParameters["q"]
    val type = call.request.queryParameters["type"]
    val fields = call.request.queryParameters.getAll("field")
}
```

## Typed Parameters

While InspeKtor documents parameters as strings by default, your implementation can parse them:

```kotlin
get("/users/{id}") {
    val id = call.parameters["id"]?.toLongOrNull()
        ?: throw BadRequestException("Invalid user ID")
}

get("/products") {
    val minPrice = call.request.queryParameters["minPrice"]?.toDoubleOrNull()
    val maxPrice = call.request.queryParameters["maxPrice"]?.toDoubleOrNull()
    val inStock = call.request.queryParameters["inStock"]?.toBoolean()
}
```

## Required vs Optional

- **Path parameters** are always required
- **Query parameters** are optional by default
- **Header parameters** are optional by default

Document required parameters in your description:

```kotlin
@KtorDescription(
    summary = "Search products",
    description = """
        Search for products.

        **Required Parameters:**
        - `q` - Search query (required)

        **Optional Parameters:**
        - `page` - Page number (default: 1)
        - `size` - Page size (default: 20)
    """
)
get("/products/search") {
    val query = call.request.queryParameters["q"]
        ?: throw BadRequestException("Query parameter 'q' is required")
}
```

## Ktor Resources Integration

If you're using Ktor Resources, parameters are also documented:

```kotlin
@Resource("/users")
class Users {
    @Resource("{id}")
    class Id(val parent: Users, val id: Long)
}

routing {
    get<Users> { /* List users */ }
    get<Users.Id> { params ->
        val userId = params.id
    }
}
```

See [Ktor Resources](../advanced/ktor-resources.md) for more details.

## Best Practices

1. **Use descriptive parameter names**
   - ✅ `userId`, `productId`, `startDate`
   - ❌ `id`, `p`, `d`

2. **Document parameter constraints**
   ```kotlin
   @KtorDescription(
       description = "Page size (1-100, default: 20)"
   )
   ```

3. **Validate parameters** in your implementation

4. **Use consistent naming** across your API
   - Pick either `page`/`size` or `offset`/`limit`
   - Use `camelCase` or `snake_case` consistently

5. **Provide defaults** where appropriate

6. **Document enum values** for constrained parameters
   ```kotlin
   @KtorDescription(
       description = "Sort order: `asc` or `desc`"
   )
   ```
