# Polymorphic Types

Document sealed classes and inheritance hierarchies in your API.

## Sealed Classes

Kotlin sealed classes are automatically converted to OpenAPI `oneOf` schemas with discriminators:

```kotlin
sealed class Shape {
    data class Circle(val radius: Double) : Shape()
    data class Rectangle(val width: Double, val height: Double) : Shape()
    data class Triangle(val base: Double, val height: Double) : Shape()
}
```

Generated OpenAPI:

```yaml
Shape:
  oneOf:
    - $ref: '#/components/schemas/Circle'
    - $ref: '#/components/schemas/Rectangle'
    - $ref: '#/components/schemas/Triangle'
  discriminator:
    propertyName: type
    mapping:
      Circle: '#/components/schemas/Circle'
      Rectangle: '#/components/schemas/Rectangle'
      Triangle: '#/components/schemas/Triangle'

Circle:
  type: object
  required:
    - type
    - radius
  properties:
    type:
      type: string
    radius:
      type: number
      format: double

Rectangle:
  type: object
  required:
    - type
    - width
    - height
  properties:
    type:
      type: string
    width:
      type: number
      format: double
    height:
      type: number
      format: double
```

## Discriminator Property

The discriminator property name is configurable:

```kotlin title="build.gradle.kts"
swagger {
    documentation {
        polymorphicDiscriminator = "type" // default
    }
}
```

Change it globally:

```kotlin
polymorphicDiscriminator = "_type"      // Use _type
polymorphicDiscriminator = "kind"       // Use kind
polymorphicDiscriminator = "@class"     // Use @class
```

## Using with @SerialName

If you use kotlinx.serialization, `@SerialName` values are used in the discriminator mapping:

```kotlin
@Serializable
sealed class Event {
    @Serializable
    @SerialName("user_created")
    data class UserCreated(val userId: String) : Event()

    @Serializable
    @SerialName("user_deleted")
    data class UserDeleted(val userId: String) : Event()
}
```

Generated mapping:

```yaml
discriminator:
  propertyName: type
  mapping:
    user_created: '#/components/schemas/UserCreated'
    user_deleted: '#/components/schemas/UserDeleted'
```

## Nested Sealed Classes

Sealed classes can contain other sealed classes:

```kotlin
sealed class ApiResponse {
    sealed class Success : ApiResponse() {
        data class WithData<T>(val data: T) : Success()
        object NoContent : Success()
    }

    sealed class Error : ApiResponse() {
        data class Validation(val errors: List<String>) : Error()
        data class NotFound(val resource: String) : Error()
        data class ServerError(val message: String) : Error()
    }
}
```

## Real-World Examples

### Payment Methods

```kotlin
sealed class PaymentMethod {
    data class CreditCard(
        val number: String,
        val expiry: String,
        val cvv: String
    ) : PaymentMethod()

    data class BankTransfer(
        val accountNumber: String,
        val routingNumber: String
    ) : PaymentMethod()

    data class PayPal(
        val email: String
    ) : PaymentMethod()

    data class Crypto(
        val walletAddress: String,
        val currency: String
    ) : PaymentMethod()
}

post("/checkout") {
    val request = call.receive<CheckoutRequest>()
    // request.paymentMethod can be any of the above
}

data class CheckoutRequest(
    val items: List<CartItem>,
    val paymentMethod: PaymentMethod
)
```

### Notification Types

```kotlin
sealed class Notification {
    abstract val id: String
    abstract val timestamp: Instant

    data class Email(
        override val id: String,
        override val timestamp: Instant,
        val recipient: String,
        val subject: String,
        val body: String
    ) : Notification()

    data class SMS(
        override val id: String,
        override val timestamp: Instant,
        val phoneNumber: String,
        val message: String
    ) : Notification()

    data class Push(
        override val id: String,
        override val timestamp: Instant,
        val deviceToken: String,
        val title: String,
        val body: String
    ) : Notification()
}
```

### API Events

```kotlin
sealed class WebhookEvent {
    abstract val eventId: String
    abstract val occurredAt: Instant

    data class OrderCreated(
        override val eventId: String,
        override val occurredAt: Instant,
        val orderId: String,
        val customerId: String,
        val total: BigDecimal
    ) : WebhookEvent()

    data class OrderShipped(
        override val eventId: String,
        override val occurredAt: Instant,
        val orderId: String,
        val trackingNumber: String
    ) : WebhookEvent()

    data class OrderCancelled(
        override val eventId: String,
        override val occurredAt: Instant,
        val orderId: String,
        val reason: String
    ) : WebhookEvent()
}
```

## Sealed Interfaces

Sealed interfaces work the same way:

```kotlin
sealed interface Result<out T> {
    data class Success<T>(val value: T) : Result<T>
    data class Failure(val error: String) : Result<Nothing>
}
```

## Abstract Classes

Abstract classes with known subclasses can also be documented:

```kotlin
abstract class Animal {
    abstract val name: String
}

data class Dog(
    override val name: String,
    val breed: String
) : Animal()

data class Cat(
    override val name: String,
    val indoor: Boolean
) : Animal()
```

## Best Practices

1. **Use sealed classes** for closed hierarchies where you control all subtypes
2. **Choose meaningful discriminator names** that match your serialization format
3. **Document the discriminator** in your API description
4. **Keep hierarchies shallow** - deeply nested sealed classes can be confusing
5. **Use consistent naming** for discriminator values (snake_case or camelCase)

## JSON Serialization

When using polymorphic types, ensure your JSON serializer is configured correctly:

```kotlin
// kotlinx.serialization
val json = Json {
    classDiscriminator = "type" // Match your OpenAPI config
}

// Jackson
val mapper = jacksonObjectMapper().apply {
    // Configure type handling
}
```

The discriminator value in the JSON must match what's documented in OpenAPI:

```json
{
    "type": "Circle",
    "radius": 5.0
}
```
