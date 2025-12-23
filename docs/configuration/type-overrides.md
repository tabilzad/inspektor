# Type Overrides

Configure how specific types are represented in the generated OpenAPI schema.

## Overview

Type overrides let you control how Kotlin/Java types are serialized in your OpenAPI specification. This is useful for:

- **Date/Time types** - Map `Instant`, `LocalDate`, etc. to proper OpenAPI formats
- **Custom types** - Define schemas for domain-specific types
- **Third-party types** - Handle types from libraries you don't control
- **UUIDs and IDs** - Ensure proper string formatting

## Basic Usage

```kotlin title="build.gradle.kts"
swagger {
    documentation {
        serialOverrides {
            typeOverride("java.time.Instant") {
                serializedAs = "string"
                format = "date-time"
            }
        }
    }
}
```

## Common Overrides

### Date and Time Types

```kotlin
serialOverrides {
    // ISO 8601 date-time (2024-01-15T10:30:00Z)
    typeOverride("java.time.Instant") {
        serializedAs = "string"
        format = "date-time"
    }

    // ISO 8601 date-time with timezone
    typeOverride("java.time.ZonedDateTime") {
        serializedAs = "string"
        format = "date-time"
    }

    typeOverride("java.time.OffsetDateTime") {
        serializedAs = "string"
        format = "date-time"
    }

    // ISO 8601 date (2024-01-15)
    typeOverride("java.time.LocalDate") {
        serializedAs = "string"
        format = "date"
    }

    // ISO 8601 time (10:30:00)
    typeOverride("java.time.LocalTime") {
        serializedAs = "string"
        format = "time"
    }

    // Date-time without timezone
    typeOverride("java.time.LocalDateTime") {
        serializedAs = "string"
        format = "date-time"
    }

    // Duration (PT1H30M)
    typeOverride("java.time.Duration") {
        serializedAs = "string"
        format = "duration"
    }
}
```

### UUID

```kotlin
serialOverrides {
    typeOverride("java.util.UUID") {
        serializedAs = "string"
        format = "uuid"
    }
}
```

### URI and URL

```kotlin
serialOverrides {
    typeOverride("java.net.URI") {
        serializedAs = "string"
        format = "uri"
    }

    typeOverride("java.net.URL") {
        serializedAs = "string"
        format = "uri"
    }
}
```

### BigDecimal and BigInteger

```kotlin
serialOverrides {
    typeOverride("java.math.BigDecimal") {
        serializedAs = "string"
        format = "decimal"
        description = "Decimal number as string for precision"
    }

    typeOverride("java.math.BigInteger") {
        serializedAs = "string"
        format = "integer"
        description = "Large integer as string"
    }
}
```

### Binary Data

```kotlin
serialOverrides {
    typeOverride("kotlin.ByteArray") {
        serializedAs = "string"
        format = "byte"
        description = "Base64 encoded binary data"
    }
}
```

## Kotlinx Datetime Types

If you're using `kotlinx-datetime`:

```kotlin
serialOverrides {
    typeOverride("kotlinx.datetime.Instant") {
        serializedAs = "string"
        format = "date-time"
    }

    typeOverride("kotlinx.datetime.LocalDate") {
        serializedAs = "string"
        format = "date"
    }

    typeOverride("kotlinx.datetime.LocalDateTime") {
        serializedAs = "string"
        format = "date-time"
    }
}
```

## Custom Domain Types

### Value Classes / Inline Classes

```kotlin
// Your code
@JvmInline
value class Email(val value: String)

@JvmInline
value class UserId(val value: Long)
```

```kotlin
// Configuration
serialOverrides {
    typeOverride("com.example.Email") {
        serializedAs = "string"
        format = "email"
    }

    typeOverride("com.example.UserId") {
        serializedAs = "integer"
        format = "int64"
    }
}
```

### Custom ID Types

```kotlin
serialOverrides {
    typeOverride("com.example.OrderId") {
        serializedAs = "string"
        pattern = "^ORD-[0-9]{10}$"
        example = "ORD-0123456789"
    }
}
```

## Override Options

| Option | Type | Description |
|--------|------|-------------|
| `serializedAs` | `String` | OpenAPI type: `"string"`, `"integer"`, `"number"`, `"boolean"`, `"array"`, `"object"` |
| `format` | `String?` | OpenAPI format: `"date-time"`, `"date"`, `"uuid"`, `"email"`, `"uri"`, etc. |
| `pattern` | `String?` | Regex pattern for validation |
| `description` | `String?` | Description of the type |
| `example` | `String?` | Example value |
| `minimum` | `Number?` | Minimum value (for numbers) |
| `maximum` | `Number?` | Maximum value (for numbers) |
| `minLength` | `Int?` | Minimum length (for strings) |
| `maxLength` | `Int?` | Maximum length (for strings) |

## Using Fully Qualified Names

Always use the fully qualified class name (including package):

```kotlin
// ✅ Correct
typeOverride("java.time.Instant") { ... }
typeOverride("com.myapp.domain.UserId") { ... }

// ❌ Incorrect - won't match
typeOverride("Instant") { ... }
typeOverride("UserId") { ... }
```

## Example with Generated Schema

Given this data class:

```kotlin
data class Order(
    val id: UUID,
    val createdAt: Instant,
    val total: BigDecimal,
    val customerEmail: Email
)
```

With these overrides:

```kotlin
serialOverrides {
    typeOverride("java.util.UUID") {
        serializedAs = "string"
        format = "uuid"
    }
    typeOverride("java.time.Instant") {
        serializedAs = "string"
        format = "date-time"
    }
    typeOverride("java.math.BigDecimal") {
        serializedAs = "string"
        format = "decimal"
    }
    typeOverride("com.example.Email") {
        serializedAs = "string"
        format = "email"
    }
}
```

Generates:

```yaml
Order:
  type: object
  required:
    - id
    - createdAt
    - total
    - customerEmail
  properties:
    id:
      type: string
      format: uuid
    createdAt:
      type: string
      format: date-time
    total:
      type: string
      format: decimal
    customerEmail:
      type: string
      format: email
```

## Complete Example

```kotlin title="build.gradle.kts"
swagger {
    documentation {
        serialOverrides {
            // Java time types
            typeOverride("java.time.Instant") {
                serializedAs = "string"
                format = "date-time"
            }
            typeOverride("java.time.LocalDate") {
                serializedAs = "string"
                format = "date"
            }
            typeOverride("java.time.LocalDateTime") {
                serializedAs = "string"
                format = "date-time"
            }
            typeOverride("java.time.Duration") {
                serializedAs = "string"
                format = "duration"
            }

            // Common types
            typeOverride("java.util.UUID") {
                serializedAs = "string"
                format = "uuid"
            }
            typeOverride("java.net.URI") {
                serializedAs = "string"
                format = "uri"
            }
            typeOverride("java.math.BigDecimal") {
                serializedAs = "string"
                format = "decimal"
            }

            // Kotlinx datetime (if used)
            typeOverride("kotlinx.datetime.Instant") {
                serializedAs = "string"
                format = "date-time"
            }

            // Custom domain types
            typeOverride("com.myapp.Email") {
                serializedAs = "string"
                format = "email"
            }
            typeOverride("com.myapp.PhoneNumber") {
                serializedAs = "string"
                pattern = "^\\+[1-9]\\d{1,14}$"
            }
        }
    }
}
```
