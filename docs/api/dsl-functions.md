# DSL Functions

Reference for InspeKtor's response documentation functions.

## responds

Documents a response type for an endpoint.

```kotlin
import io.github.tabilzad.ktor.responds
```

### Signatures

```kotlin
// Basic usage
inline fun <reified T> PipelineContext<*, ApplicationCall>.responds(
    status: HttpStatusCode
)

// With description
inline fun <reified T> PipelineContext<*, ApplicationCall>.responds(
    status: HttpStatusCode, description: String
)

// With content type
inline fun <reified T> PipelineContext<*, ApplicationCall>.responds(
    status: HttpStatusCode, contentType: String
)

// Full options
inline fun <reified T> PipelineContext<*, ApplicationCall>.responds(
    status: HttpStatusCode, description: String, contentType: String
)
```

### Parameters

| Parameter     | Type             | Required | Description                                |
|---------------|------------------|----------|--------------------------------------------|
| `T`           | Type parameter   | Yes      | The response body type                     |
| `status`      | `HttpStatusCode` | Yes      | HTTP status code                           |
| `description` | `String`         | No       | Response description                       |
| `contentType` | `String`         | No       | Content type (default: `application/json`) |

### Usage Examples

**Basic response:**

```kotlin
get("/users") {
    responds<List<User>>(HttpStatusCode.OK)
}
```

**With description:**

```kotlin
post("/users") {
    responds<User>(HttpStatusCode.Created, description = "User successfully created")
    responds<ErrorResponse>(HttpStatusCode.BadRequest, description = "Invalid user data")
}
```

**With custom content type:**

```kotlin
get("/report") {
    responds<ByteArray>(HttpStatusCode.OK, contentType = "application/pdf")
}
```

**Multiple responses:**

```kotlin
get("/users/{id}") {
    responds<User>(HttpStatusCode.OK, description = "User found")
    responds<ErrorResponse>(HttpStatusCode.BadRequest, description = "Invalid ID format")
    responds<ErrorResponse>(HttpStatusCode.NotFound, description = "User not found")
    responds<ErrorResponse>(HttpStatusCode.InternalServerError, description = "Server error")
}
```

### Supported Types

| Type                | OpenAPI Schema                          |
|---------------------|-----------------------------------------|
| `User` (data class) | `$ref: '#/components/schemas/User'`     |
| `List<User>`        | `type: array, items: $ref...`           |
| `Map<String, User>` | `type: object, additionalProperties...` |
| `String`            | `type: string`                          |
| `Int`, `Long`       | `type: integer`                         |
| `Double`, `Float`   | `type: number`                          |
| `Boolean`           | `type: boolean`                         |
| `ByteArray`         | `type: string, format: byte`            |

### Generated OpenAPI

```kotlin
get("/users/{id}") {
    responds<User>(HttpStatusCode.OK, description = "User found")
    responds<ErrorResponse>(HttpStatusCode.NotFound, description = "User not found")
}
```

Generates:

```yaml
/users/{id}:
  get:
    responses:
      "200":
        description: "User found"
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/User'
      "404":
        description: "User not found"
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ErrorResponse'
```

---

## respondsNothing

Documents a response with no body (e.g., 204 No Content).

```kotlin
import io.github.tabilzad.ktor.respondsNothing
```

### Signatures

```kotlin
// Basic usage
fun PipelineContext<*, ApplicationCall>.respondsNothing(
    status: HttpStatusCode
)

// With description
fun PipelineContext<*, ApplicationCall>.respondsNothing(
    status: HttpStatusCode,
    description: String
)
```

### Parameters

| Parameter     | Type             | Required | Description          |
|---------------|------------------|----------|----------------------|
| `status`      | `HttpStatusCode` | Yes      | HTTP status code     |
| `description` | `String`         | No       | Response description |

### Usage Examples

**Basic usage:**

```kotlin
delete("/users/{id}") {
    respondsNothing(HttpStatusCode.NoContent)
}
```

**With description:**

```kotlin
delete("/users/{id}") {
    respondsNothing(HttpStatusCode.NoContent, description = "User successfully deleted")
    responds<ErrorResponse>(HttpStatusCode.NotFound, description = "User not found")
}
```

### Common Status Codes

| Status Code             | Use Case                                     |
|-------------------------|----------------------------------------------|
| `204 No Content`        | Successful DELETE, PUT with no response body |
| `202 Accepted`          | Async operation accepted                     |
| `301 Moved Permanently` | Redirect (no body)                           |
| `304 Not Modified`      | Cache validation                             |

### Generated OpenAPI

```kotlin
delete("/users/{id}") {
    respondsNothing(HttpStatusCode.NoContent, description = "User deleted")
}
```

Generates:

```yaml
/users/{id}:
  delete:
    responses:
      "204":
        description: "User deleted"
```

---

## Best Practices

### 1. Document All Response Codes

```kotlin
get("/users/{id}") {
    // Success case
    responds<User>(HttpStatusCode.OK)

    // Client errors
    responds<ErrorResponse>(HttpStatusCode.BadRequest)
    responds<ErrorResponse>(HttpStatusCode.Unauthorized)
    responds<ErrorResponse>(HttpStatusCode.Forbidden)
    responds<ErrorResponse>(HttpStatusCode.NotFound)

    // Server errors
    responds<ErrorResponse>(HttpStatusCode.InternalServerError)
}
```

### 2. Use Meaningful Descriptions

```kotlin
// Good
responds<User>(HttpStatusCode.OK, description = "Returns the requested user")
responds<ErrorResponse>(HttpStatusCode.NotFound, description = "User with specified ID not found")

// Avoid
responds<User>(HttpStatusCode.OK)  // No description
responds<User>(HttpStatusCode.OK, description = "OK")  // Redundant
```

### 3. Use Consistent Error Types

```kotlin
// Define standard error response
data class ErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null
)

// Use consistently
responds<ErrorResponse>(HttpStatusCode.BadRequest)
responds<ErrorResponse>(HttpStatusCode.NotFound)
responds<ErrorResponse>(HttpStatusCode.InternalServerError)
```

### 4. Document Collection Responses

```kotlin
// List response
responds<List<User>>(HttpStatusCode.OK)

// Paginated response
responds<Page<User>>(HttpStatusCode.OK)

// Empty list is still 200
responds<List<User>>(HttpStatusCode.OK, description = "Returns users (may be empty)")
```

### 5. Place responds Calls at Route Start

```kotlin
get("/users/{id}") {
    // Document responses first
    responds<User>(HttpStatusCode.OK)
    responds<ErrorResponse>(HttpStatusCode.NotFound)

    // Then implementation
    val userId = call.parameters["id"]?.toLongOrNull()
        ?: return@get call.respond(HttpStatusCode.BadRequest)

    val user = userService.findById(userId)
        ?: return@get call.respond(HttpStatusCode.NotFound)

    call.respond(user)
}
```

---

## Complete Example

```kotlin
import io.github.tabilzad.ktor.responds
import io.github.tabilzad.ktor.respondsNothing
import io.github.tabilzad.ktor.annotations.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

@Tag(["Users"])
fun Route.completeUserRoutes() {
    route("/users") {

        @KtorDescription(summary = "List all users")
        get {
            responds<List<User>>(HttpStatusCode.OK, description = "List of all users")
        }

        @KtorDescription(summary = "Create a new user")
        post {
            responds<User>(HttpStatusCode.Created, description = "User created successfully")
            responds<ErrorResponse>(HttpStatusCode.BadRequest, description = "Invalid request body")
            responds<ErrorResponse>(HttpStatusCode.Conflict, description = "Email already exists")

            val request = call.receive<CreateUserRequest>()
            // Implementation
        }

        route("/{id}") {

            @KtorDescription(summary = "Get user by ID")
            get {
                responds<User>(HttpStatusCode.OK, description = "User found")
                responds<ErrorResponse>(HttpStatusCode.BadRequest, description = "Invalid user ID")
                responds<ErrorResponse>(HttpStatusCode.NotFound, description = "User not found")
            }

            @KtorDescription(summary = "Update user")
            put {
                responds<User>(HttpStatusCode.OK, description = "User updated successfully")
                responds<ErrorResponse>(HttpStatusCode.BadRequest, description = "Invalid request")
                responds<ErrorResponse>(HttpStatusCode.NotFound, description = "User not found")

                val request = call.receive<UpdateUserRequest>()
                // Implementation
            }

            @KtorDescription(summary = "Delete user")
            delete {
                respondsNothing(HttpStatusCode.NoContent, description = "User deleted")
                responds<ErrorResponse>(HttpStatusCode.NotFound, description = "User not found")
            }
        }
    }
}
```
