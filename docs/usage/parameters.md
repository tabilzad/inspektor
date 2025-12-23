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

Header parameters are detected from `call.request.headers`:

```kotlin
get("/users") {
    val apiVersion = call.request.headers["X-API-Version"]
    val requestId = call.request.headers["X-Request-ID"]
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
```

## Parameter Descriptions

Add descriptions using KtorDescription:

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
