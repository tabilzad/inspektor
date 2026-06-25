# Responses

Document your API's response types and status codes.

## Automatic Response Inference

InspeKtor can infer responses directly from your handler's `call.respond*(...)` calls, so for the
common case you don't need the `responds<T>()` DSL or `@KtorResponds` at all.

It is **opt-in** while in alpha — enable it in the Gradle DSL:

```kotlin
swagger {
    documentation {
        inferResponseSchemas = true // default: false
    }
}
```

With it enabled:

```kotlin
get("/users/{id}") {
    val user: User = service.find(id)
    call.respond(user)                                  // -> 200, schema User
    call.respond(HttpStatusCode.NotFound, ErrorResponse(...)) // -> 404, schema ErrorResponse
}
```

```yaml
/users/{id}:
  get:
    responses:
      "200":
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/User'
      "404":
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ErrorResponse'
```

What is inferred:

- **Body type** — the static type of the responded value (works through `val` bindings, control-flow
  branches, and **extracted handler functions** that take the `call`, e.g. `get { handle(call) }`).
  Sealed (`oneOf`), generic, and value-class types reuse the same schema generator as request bodies.
- **Status code** — from an `HttpStatusCode` argument (e.g. `HttpStatusCode.Created` → `201`),
  otherwise `200` (`respondRedirect` → `302`).
- **Content type** — `respondText` → `text/plain`, `respondBytes` → `application/octet-stream`,
  `respondFile`/`respondPath` → `*/*`, otherwise `application/json`.
- **Multiple responses** — every `respond*` site in the handler (success + error branches, early
  `return@get`, and inside nested lambdas / custom DSLs such as `withContext { … }`) contributes a
  response.

### Precedence

Explicit declarations always win. For a given status code, `responds<T>()` / `@KtorResponds` override
the inferred response; inference only fills in the status codes you didn't declare.

### Limitations

- Disabled by default (opt-in) for the first alpha — enabling it changes generated specs.
- Erased (`Any`) bodies, and generics resolved only through a generic extracted function, emit the
  response (status + content type) **without** a schema rather than a misleading one.
- A non-constant status code (a variable or `HttpStatusCode(code, …)`) cannot be read statically and
  defaults to `200`; `respondRedirect` is always documented as `302` (a `permanent = true` 301 is not
  detected). Declare these explicitly with `responds<T>()` if needed.
- Inference descends into nested lambdas and custom DSLs, so a `respond` inside a **detached** builder
  (`launch { … }`, `async { … }`) is also attributed to the endpoint even though it doesn't produce
  the HTTP response. Avoid responding from detached coroutines, or declare the real responses
  explicitly. (Scope-aware detection is a planned follow-up.)
- Explicit `ContentType` arguments and response headers are not inferred yet (use `@KtorDescription`
  / the DSL). `respondHtml` (from `ktor-server-html-builder`) is not recognised.

## The responds Function

Use the `responds` function to document response types:

```kotlin
import io.github.tabilzad.ktor.responds

get("/users/{id}") {
    responds<User>(HttpStatusCode.OK)
    responds<ErrorResponse>(HttpStatusCode.NotFound)

    // Implementation
}
```

Generated OpenAPI:

```yaml
/users/{id}:
  get:
    responses:
      "200":
        description: "OK"
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/User'
      "404":
        description: "Not Found"
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ErrorResponse'
```

## Response with Description

Add custom descriptions to responses:

```kotlin
post("/users") {
    responds<User>(
        HttpStatusCode.Created,
        description = "User successfully created"
    )
    responds<ErrorResponse>(
        HttpStatusCode.BadRequest,
        description = "Invalid user data provided"
    )
    responds<ErrorResponse>(
        HttpStatusCode.Conflict,
        description = "User with this email already exists"
    )
}
```

## Empty Responses

For endpoints that don't return a body:

```kotlin
delete("/users/{id}") {
    respondsNothing(HttpStatusCode.NoContent)
    responds<ErrorResponse>(HttpStatusCode.NotFound)
}
```

Generated:

```yaml
responses:
  "204":
    description: "No Content"
  "404":
    description: "Not Found"
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/ErrorResponse'
```

## Collection Responses

Document list/array responses:

```kotlin
get("/users") {
    responds<List<User>>(HttpStatusCode.OK)
}
```

Generated:

```yaml
responses:
  "200":
    content:
      application/json:
        schema:
          type: array
          items:
            $ref: '#/components/schemas/User'
```

## Common HTTP Status Codes

Here are common status codes and their typical usage:

### Success (2xx)

```kotlin
// 200 OK - Successful GET, PUT, PATCH
get("/resource") {
    responds<Resource>(HttpStatusCode.OK)
}

// 201 Created - Successful POST that creates a resource
post("/resource") {
    responds<Resource>(HttpStatusCode.Created)
}

// 202 Accepted - Request accepted for async processing
post("/jobs") {
    responds<Job>(HttpStatusCode.Accepted, description = "Job queued for processing")
}

// 204 No Content - Successful DELETE or update with no body
delete("/resource/{id}") {
    respondsNothing(HttpStatusCode.NoContent)
}
```

### Client Errors (4xx)

```kotlin
get("/resource/{id}") {
    responds<Resource>(HttpStatusCode.OK)

    // 400 Bad Request - Invalid input
    responds<ErrorResponse>(HttpStatusCode.BadRequest, description = "Invalid request parameters")

    // 401 Unauthorized - Authentication required
    responds<ErrorResponse>(HttpStatusCode.Unauthorized, description = "Authentication required")

    // 403 Forbidden - Not allowed
    responds<ErrorResponse>(HttpStatusCode.Forbidden, description = "Insufficient permissions")

    // 404 Not Found - Resource doesn't exist
    responds<ErrorResponse>(HttpStatusCode.NotFound, description = "Resource not found")

    // 409 Conflict - Resource conflict
    responds<ErrorResponse>(HttpStatusCode.Conflict, description = "Resource already exists")

    // 422 Unprocessable Entity - Validation failed
    responds<ValidationErrorResponse>(HttpStatusCode.UnprocessableEntity, description = "Validation failed")

    // 429 Too Many Requests - Rate limited
    responds<ErrorResponse>(HttpStatusCode.TooManyRequests, description = "Rate limit exceeded")
}
```

### Server Errors (5xx)

```kotlin
get("/resource") {
    responds<Resource>(HttpStatusCode.OK)

    // 500 Internal Server Error
    responds<ErrorResponse>(HttpStatusCode.InternalServerError, description = "An unexpected error occurred")

    // 503 Service Unavailable
    responds<ErrorResponse>(HttpStatusCode.ServiceUnavailable, description = "Service temporarily unavailable")
}
```

## Response Schemas

### Standard Error Response

Define a consistent error response:

```kotlin
data class ErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null
)
```

### Validation Error Response

```kotlin
data class ValidationErrorResponse(
    val message: String,
    val errors: List<FieldError>
)

data class FieldError(
    val field: String,
    val message: String
)
```

### Paginated Response

```kotlin
data class PaginatedResponse<T>(
    val data: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Long,
    val totalPages: Int
)

get("/users") {
    responds<PaginatedResponse<User>>(HttpStatusCode.OK)
}
```

## Multiple Response Types

Document all possible responses:

```kotlin
@KtorDescription(summary = "Get user by ID")
get("/users/{id}") {
    responds<User>(HttpStatusCode.OK, description = "User found")
    responds<ErrorResponse>(HttpStatusCode.BadRequest, description = "Invalid user ID format")
    responds<ErrorResponse>(HttpStatusCode.Unauthorized, description = "Not authenticated")
    responds<ErrorResponse>(HttpStatusCode.Forbidden, description = "Not authorized to view this user")
    responds<ErrorResponse>(HttpStatusCode.NotFound, description = "User not found")
    responds<ErrorResponse>(HttpStatusCode.InternalServerError, description = "Server error")

    // Implementation
}
```

## Response Headers

Document response headers using KtorDescription:

```kotlin
@KtorDescription(
    summary = "List users with pagination",
    description = "Returns X-Total-Count header with total number of users"
)
get("/users") {
    responds<List<User>>(HttpStatusCode.OK)
}
```

## Content Types

By default, responses are documented as `application/json`. For other content types:

```kotlin
@KtorDescription(summary = "Download file")
get("/files/{id}") {
    responds<ByteArray>(HttpStatusCode.OK, contentType = "application/octet-stream")
}

@KtorDescription(summary = "Get user avatar")
get("/users/{id}/avatar") {
    responds<ByteArray>(HttpStatusCode.OK, contentType = "image/png")
}
```

## Best Practices

1. **Document all responses** - Include success and error cases
2. **Use consistent error schemas** - Define standard error types
3. **Add descriptions** - Explain what each status code means in context
4. **Be specific** - Different error codes for different failure modes
5. **Include examples** - Use KDoc to provide example values
