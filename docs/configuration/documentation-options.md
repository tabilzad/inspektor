# Documentation Options

These options control how your API documentation is generated, including metadata, schema handling, and description
extraction.

## API Information

Configure the basic information that appears in your OpenAPI specification:

```kotlin title="build.gradle.kts"
swagger {
    documentation {
        info {
            title = "My API"
            description = "A comprehensive REST API"
            version = "1.0.0"

            contact {
                name = "API Support Team"
                url = "https://example.com/support"
                email = "api-support@example.com"
            }

            license {
                name = "Apache 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }
    }
}
```

### Info Options

| Option          | Type     | Default                              | Description                     |
|-----------------|----------|--------------------------------------|---------------------------------|
| `title`         | `String` | `"Open API Specification"`           | The title of your API           |
| `description`   | `String` | `"Generated using Ktor Docs Plugin"` | A brief description of your API |
| `version`       | `String` | `"1.0.0"`                            | The version of your API         |
| `contact.name`  | `String` | `null`                               | Contact person/team name        |
| `contact.url`   | `String` | `null`                               | Contact URL                     |
| `contact.email` | `String` | `null`                               | Contact email                   |
| `license.name`  | `String` | `null`                               | License name                    |
| `license.url`   | `String` | `null`                               | License URL                     |

## Server URLs

Define the server URLs where your API is hosted:

```kotlin title="build.gradle.kts"
swagger {
    documentation {
        servers = listOf(
            "https://api.example.com",
            "https://staging.example.com",
            "http://localhost:8080"
        )
    }
}
```

These appear in the OpenAPI spec as:

```yaml
servers:
  - url: https://api.example.com
  - url: https://staging.example.com
  - url: http://localhost:8080
```

## Schema Generation Options

### generateRequestSchemas

Controls whether request body schemas are automatically generated from `call.receive<T>()` calls.

```kotlin
generateRequestSchemas = true // default
```

When enabled:

```kotlin
post("/users") {
    val user = call.receive<CreateUserRequest>() // Schema auto-generated
}
```

### hideTransientFields

Excludes fields marked with `@Transient` from generated schemas.

```kotlin
hideTransientFields = true // default
```

```kotlin
data class User(
    val id: Long,
    val name: String,
    @Transient
    val internalToken: String // Excluded from schema
)
```

### hidePrivateAndInternalFields

Excludes `private` and `internal` fields from generated schemas.

```kotlin
hidePrivateAndInternalFields = true // default
```

```kotlin
data class User(
    val id: Long,           // Included
    val name: String,       // Included
    private val secret: String,  // Excluded
    internal val cache: Map<String, Any>  // Excluded
)
```

### deriveFieldRequirementFromTypeNullability

Automatically determines if fields are required based on nullability.

```kotlin
deriveFieldRequirementFromTypeNullability = true // default
```

```kotlin
data class CreateUserRequest(
    val name: String,       // required: true (non-nullable)
    val email: String,      // required: true (non-nullable)
    val nickname: String?   // required: false (nullable)
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
    nickname:
      type: string
```

### useKDocsForDescriptions

Extracts descriptions from KDoc comments on classes and properties.

```kotlin
useKDocsForDescriptions = true // default
```

```kotlin
/**
 * Represents a user in the system
 */
data class User(
    /** Unique identifier */
    val id: Long,
    /** User's display name */
    val name: String
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
      description: "Unique identifier"
    name:
      type: string
      description: "User's display name"
```

### polymorphicDiscriminator

Sets the default discriminator property name for sealed class hierarchies.

```kotlin
polymorphicDiscriminator = "type" // default
```

Used with sealed classes:

```kotlin
sealed class PaymentMethod {
    data class CreditCard(val number: String) : PaymentMethod()
    data class BankTransfer(val iban: String) : PaymentMethod()
}
```

Generated schema uses `type` as the discriminator:

```yaml
PaymentMethod:
  oneOf:
    - $ref: '#/components/schemas/CreditCard'
    - $ref: '#/components/schemas/BankTransfer'
  discriminator:
    propertyName: type
```

## Complete Reference

| Option                                      | Type           | Default                    | Description                      |
|---------------------------------------------|----------------|----------------------------|----------------------------------|
| `info.title`                                | `String`       | `"Open API Specification"` | API title                        |
| `info.description`                          | `String`       | `"Generated using..."`     | API description                  |
| `info.version`                              | `String`       | `"1.0.0"`                  | API version                      |
| `servers`                                   | `List<String>` | `[]`                       | Server URLs                      |
| `generateRequestSchemas`                    | `Boolean`      | `true`                     | Auto-generate request schemas    |
| `hideTransientFields`                       | `Boolean`      | `true`                     | Hide @Transient fields           |
| `hidePrivateAndInternalFields`              | `Boolean`      | `true`                     | Hide private/internal fields     |
| `deriveFieldRequirementFromTypeNullability` | `Boolean`      | `true`                     | Derive required from nullability |
| `useKDocsForDescriptions`                   | `Boolean`      | `true`                     | Extract KDoc descriptions        |
| `polymorphicDiscriminator`                  | `String`       | `"type"`                   | Sealed class discriminator       |
