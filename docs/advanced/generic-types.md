# Generic Types

Document generic classes and type parameters in your API schemas.

## Basic Generic Types

InspeKtor resolves generic type parameters when generating schemas:

```kotlin
data class Response<T>(
    val data: T,
    val timestamp: Instant
)

get("/users/{id}") {
    responds<Response<User>>(HttpStatusCode.OK)
}
```

Generated schemas:

```yaml
ResponseUser: # Generated name combines wrapper + type parameter
  type: object
  required:
    - data
    - timestamp
  properties:
    data:
      $ref: '#/components/schemas/User'
    timestamp:
      type: string
      format: date-time

User:
  type: object
  properties:
    id:
      type: integer
    name:
      type: string
```

## Common Generic Patterns

### API Response Wrapper

```kotlin
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: String?,
    val timestamp: Instant = Instant.now()
)

// Usage
get("/users") {
    responds<ApiResponse<List<User>>>(HttpStatusCode.OK)
}

get("/users/{id}") {
    responds<ApiResponse<User>>(HttpStatusCode.OK)
    responds<ApiResponse<Nothing>>(HttpStatusCode.NotFound)
}
```

### Paginated Response

```kotlin
data class Page<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean
)

get("/users") {
    responds<Page<User>>(HttpStatusCode.OK)
}

get("/products") {
    responds<Page<Product>>(HttpStatusCode.OK)
}
```

### Result Type

```kotlin
sealed class Result<out T> {
    data class Success<T>(val value: T) : Result<T>()
    data class Error(val message: String, val code: String) : Result<Nothing>()
}

get("/users/{id}") {
    responds<Result<User>>(HttpStatusCode.OK)
}
```

## Multiple Type Parameters

```kotlin
data class Pair<A, B>(
    val first: A,
    val second: B
)

data class Either<L, R>(
    val left: L?,
    val right: R?
)

get("/comparison") {
    responds<Pair<User, User>>(HttpStatusCode.OK)
}
```

## Nested Generics

```kotlin
data class Response<T>(
    val data: T
)

data class PagedData<T>(
    val items: List<T>,
    val total: Int
)

// Nested generic usage
get("/users") {
    responds<Response<PagedData<User>>>(HttpStatusCode.OK)
}
```

## Generic Constraints

Kotlin's generic constraints are respected:

```kotlin
data class NumericRange<T : Number>(
    val min: T,
    val max: T
)

data class ComparableWrapper<T : Comparable<T>>(
    val value: T
)
```

## Collections with Generics

Standard collections work seamlessly:

```kotlin
get("/users") {
    responds<List<User>>(HttpStatusCode.OK)
}

get("/user-map") {
    responds<Map<String, User>>(HttpStatusCode.OK)
}

get("/user-set") {
    responds<Set<UserId>>(HttpStatusCode.OK)
}
```

Generated:

```yaml
# List<User>
type: array
items:
  $ref: '#/components/schemas/User'

# Map<String, User>
type: object
additionalProperties:
  $ref: '#/components/schemas/User'

# Set<UserId>
type: array
items:
  $ref: '#/components/schemas/UserId'
uniqueItems: true
```

## Generic Functions

Generic route functions are supported:

```kotlin
inline fun <reified T : Any> Route.crudRoutes(
    path: String,
    crossinline handler: suspend (ApplicationCall) -> Unit
) {
    route(path) {
        get {
            responds<List<T>>(HttpStatusCode.OK)
            handler(call)
        }
    }
}

// Usage - T is resolved to User
crudRoutes<User>("/users") { call ->
    // Implementation
}
```

## Schema Naming

InspeKtor generates unique schema names for each generic instantiation:

| Generic Type           | Generated Schema Name |
|------------------------|-----------------------|
| `Response<User>`       | `ResponseUser`        |
| `Response<List<User>>` | `ResponseListUser`    |
| `Page<Product>`        | `PageProduct`         |
| `Either<Error, User>`  | `EitherErrorUser`     |

## Nullable Type Parameters

Nullable generics are handled correctly:

```kotlin
data class Optional<T>(
    val value: T?
)

get("/user") {
    responds<Optional<User>>(HttpStatusCode.OK)
}
```

## Real-World Example

Here's a complete example of a generic API response structure:

```kotlin
// Generic wrappers
data class ApiResponse<T>(
    val data: T?,
    val meta: ResponseMeta,
    val errors: List<ApiError>?
)

data class ResponseMeta(
    val requestId: String,
    val timestamp: Instant,
    val duration: Long
)

data class ApiError(
    val code: String,
    val message: String,
    val field: String?
)

data class PagedResponse<T>(
    val items: List<T>,
    val pagination: PaginationInfo
)

data class PaginationInfo(
    val page: Int,
    val size: Int,
    val total: Long,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)

// Usage in routes
@Tag(["Users"])
fun Route.userRoutes() {
    route("/users") {
        @KtorDescription(summary = "List users with pagination")
        get {
            responds<ApiResponse<PagedResponse<User>>>(HttpStatusCode.OK)
        }

        @KtorDescription(summary = "Get user by ID")
        get("/{id}") {
            responds<ApiResponse<User>>(HttpStatusCode.OK)
            responds<ApiResponse<Nothing>>(HttpStatusCode.NotFound)
        }

        @KtorDescription(summary = "Create user")
        post {
            responds<ApiResponse<User>>(HttpStatusCode.Created)
            responds<ApiResponse<Nothing>>(HttpStatusCode.BadRequest)
        }
    }
}
```

## Best Practices

1. **Use meaningful wrapper names** - `ApiResponse<T>` is clearer than `R<T>`
2. **Keep nesting shallow** - `Response<Page<List<User>>>` is hard to understand
3. **Document type parameters** with KDoc
4. **Be consistent** - Use the same wrapper types throughout your API
5. **Consider type aliases** for complex types:

```kotlin
typealias UserResponse = ApiResponse<User>
typealias UserListResponse = ApiResponse<PagedResponse<User>>
```
