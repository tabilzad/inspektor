# Sample Project

The best way to learn InspeKtor is by exploring a complete working example.

## Official Sample Repository

We maintain an official sample project that demonstrates all of InspeKtor's features:

<div class="grid cards" markdown>

- [:material-github: **ktor-inspektor-example**](https://github.com/tabilzad/ktor-inspektor-example)

  A complete Ktor server with InspeKtor integration showcasing:

    - Basic routing and endpoint documentation
    - Request and response schemas
    - Authentication and security schemes
    - Tags and organization
    - Custom type overrides

</div>

## Clone and Run

```bash
# Clone the sample project
git clone https://github.com/tabilzad/ktor-inspektor-example.git
cd ktor-inspektor-example

# Build and generate the OpenAPI spec
./gradlew build

# Run the server
./gradlew run
```

The generated OpenAPI spec will be available at `build/resources/main/openapi/openapi.yaml`.

## Project Structure

```
ktor-inspektor-example/
├── build.gradle.kts          # Plugin configuration
├── src/main/kotlin/
│   ├── Application.kt        # Main application with @GenerateOpenApi
│   ├── routes/
│   │   ├── UserRoutes.kt     # User CRUD endpoints
│   │   ├── ProductRoutes.kt  # Product endpoints
│   │   └── OrderRoutes.kt    # Order endpoints
│   └── models/
│       ├── User.kt           # User data classes
│       ├── Product.kt        # Product data classes
│       └── Order.kt          # Order data classes
└── build/resources/main/openapi/
    └── openapi.yaml          # Generated specification
```

## Key Examples in the Sample

### Basic Routing

```kotlin
@GenerateOpenApi
fun Application.module() {
    routing {
        userRoutes()
        productRoutes()
        orderRoutes()
    }
}
```

### CRUD Operations

```kotlin
@Tag(["Users"])
fun Route.userRoutes() {
    route("/users") {
        get { /* List users */ }
        post { /* Create user */ }
        route("/{id}") {
            get { /* Get user */ }
            put { /* Update user */ }
            delete { /* Delete user */ }
        }
    }
}
```

### Request/Response Documentation

```kotlin
@KtorDescription(
    summary = "Create a new order",
    description = "Creates an order with the specified items"
)
post {
    responds<Order>(HttpStatusCode.Created, description = "Order created successfully")
    responds<ErrorResponse>(HttpStatusCode.BadRequest, description = "Invalid order data")

    val request = call.receive<CreateOrderRequest>()
    // ...
}
```

## Try It Yourself

1. Clone the sample project
2. Explore the code and configuration
3. Modify the routes and see how the spec changes
4. Use the generated spec with Swagger UI or other OpenAPI tools

!!! tip "Swagger UI"
You can visualize your generated spec using [Swagger Editor](https://editor.swagger.io/) - just paste your YAML content
or upload the file.
