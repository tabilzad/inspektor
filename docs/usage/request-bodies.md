# Request Bodies

Document the data your API accepts.

## Automatic Detection

InspeKtor automatically detects request bodies from `call.receive<T>()` calls:

```kotlin
post("/users") {
    val request = call.receive<CreateUserRequest>()
    // CreateUserRequest schema is automatically generated
}
```

This generates:

```yaml
/users:
  post:
    requestBody:
      required: true
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/CreateUserRequest'
```

## Data Class Schemas

Your Kotlin data classes are converted to OpenAPI schemas:

```kotlin
data class CreateUserRequest(
    val name: String,
    val email: String,
    val age: Int?,
    val roles: List<String> = emptyList()
)
```

Generated schema:

```yaml
CreateUserRequest:
  type: object
  required:
    - name
    - email
  properties:
    name:
      type: string
    email:
      type: string
    age:
      type: integer
      format: int32
    roles:
      type: array
      items:
        type: string
```

## Nullability and Required Fields

By default, field requirement is derived from Kotlin nullability:

| Kotlin Type | OpenAPI Required |
|-------------|------------------|
| `String`    | Yes              |
| `String?`   | No               |
| `Int`       | Yes              |
| `Int?`      | No               |

This behavior is controlled by `deriveFieldRequirementFromTypeNullability`:

```kotlin
swagger {
    documentation {
        deriveFieldRequirementFromTypeNullability = true // default
    }
}
```

## Default Values

Fields with default values in data classes:

```kotlin
data class SearchRequest(
    val query: String,
    val page: Int = 1,
    val size: Int = 20
)
```

!!! note
Currently, default values don't affect the `required` status in OpenAPI. Use nullable types if you want fields to be
optional.

## Nested Objects

Nested data classes are fully supported:

```kotlin
data class CreateOrderRequest(
    val customer: CustomerInfo,
    val items: List<OrderItem>,
    val shippingAddress: Address
)

data class CustomerInfo(
    val name: String,
    val email: String
)

data class OrderItem(
    val productId: String,
    val quantity: Int
)

data class Address(
    val street: String,
    val city: String,
    val country: String
)
```

All nested types are included in the components/schemas section.

## Collections

Various collection types are supported:

```kotlin
data class BatchRequest(
    val ids: List<Long>,           // array of integers
    val tags: Set<String>,         // array of strings (unique)
    val metadata: Map<String, Any> // object with additionalProperties
)
```

## Enums

Kotlin enums generate proper OpenAPI enums:

```kotlin
enum class OrderStatus {
    PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED
}

data class UpdateOrderRequest(
    val status: OrderStatus
)
```

Generated:

```yaml
OrderStatus:
  type: string
  enum:
    - PENDING
    - PROCESSING
    - SHIPPED
    - DELIVERED
    - CANCELLED
```

## KDoc Descriptions

Add descriptions using KDoc comments:

```kotlin
/**
 * Request to create a new user account
 */
data class CreateUserRequest(
    /** User's full name */
    val name: String,

    /** Valid email address */
    val email: String,

    /** User's age (must be 18 or older) */
    val age: Int?
)
```

Generated:

```yaml
CreateUserRequest:
  type: object
  description: "Request to create a new user account"
  properties:
    name:
      type: string
      description: "User's full name"
    email:
      type: string
      description: "Valid email address"
    age:
      type: integer
      description: "User's age (must be 18 or older)"
```

Enable this with:

```kotlin
swagger {
    documentation {
        useKDocsForDescriptions = true // default
    }
}
```

## Excluding Fields

### @Transient Annotation

```kotlin
data class UserRequest(
    val name: String,
    @Transient
    val internalId: String // Excluded from schema
)
```

### Private/Internal Fields

```kotlin
data class UserRequest(
    val name: String,
    private val secret: String,    // Excluded
    internal val cache: String     // Excluded
)
```

Control this behavior:

```kotlin
swagger {
    documentation {
        hideTransientFields = true              // default
        hidePrivateAndInternalFields = true     // default
    }
}
```

## Multiple Receive Calls

If you have conditional receive logic, all types are documented:

```kotlin
post("/upload") {
    val contentType = call.request.contentType()
    when {
        contentType.match(ContentType.Application.Json) -> {
            val json = call.receive<JsonUpload>()
        }
        contentType.match(ContentType.MultiPart.FormData) -> {
            val multipart = call.receiveMultipart()
        }
    }
}
```

## Disabling Auto-Generation

If you prefer manual schema definition, disable automatic detection:

```kotlin
swagger {
    documentation {
        generateRequestSchemas = false
    }
}
```

## Content Types

The default content type is `application/json`. For other content types, the schema still documents the expected
structure.

```kotlin
post("/upload") {
    // Form data
    val multipart = call.receiveMultipart()
}
```

## Validation Hints

While InspeKtor doesn't enforce validation, you can document constraints using KDoc:

```kotlin
data class CreateUserRequest(
    /** User's name (2-100 characters) */
    val name: String,

    /** Valid email address format */
    val email: String,

    /** Age between 18 and 120 */
    val age: Int
)
```

For actual validation, use a validation library like Konform or javax.validation, and document the constraints in KDoc.
