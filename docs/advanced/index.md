# Advanced Topics

Explore advanced features and integration patterns.

## Topics

<div class="grid cards" markdown>

- :material-family-tree: **[Polymorphic Types](polymorphic-types.md)**

  Document sealed classes and inheritance hierarchies with discriminators

- :material-code-braces: **[Generic Types](generic-types.md)**

  Handle generic classes and type parameters in your schemas

- :material-routes: **[Ktor Resources](ktor-resources.md)**

  Integration with Ktor's type-safe routing plugin

- :material-lightning-bolt: **[Incremental Compilation](incremental-compilation.md)**

  Optimize build performance with regeneration modes

</div>

## When to Use Advanced Features

| Feature                 | Use When                                                  |
|-------------------------|-----------------------------------------------------------|
| Polymorphic Types       | You have sealed classes or inheritance in your API models |
| Generic Types           | You use generic wrappers like `Response<T>` or `Page<T>`  |
| Ktor Resources          | You use Ktor's type-safe Resources plugin                 |
| Incremental Compilation | You want to optimize local development build times        |

## Common Advanced Patterns

### Wrapper Response Types

```kotlin
// Generic response wrapper
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: String?
)

get("/users/{id}") {
    responds<ApiResponse<User>>(HttpStatusCode.OK)
}
```

### Sealed Class Responses

```kotlin
sealed class PaymentResult {
    data class Success(val transactionId: String) : PaymentResult()
    data class Failed(val reason: String) : PaymentResult()
    data class Pending(val checkUrl: String) : PaymentResult()
}

post("/payments") {
    responds<PaymentResult>(HttpStatusCode.OK)
}
```

### Type-Safe Resources

```kotlin
@Resource("/users")
class Users {
    @Resource("{id}")
    class Id(val parent: Users, val id: Long)
}

get<Users.Id> { params ->
    responds<User>(HttpStatusCode.OK)
}
```
