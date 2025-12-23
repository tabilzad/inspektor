# Quick Start

Generate your first OpenAPI specification in under 5 minutes.

## Step 1: Add the Plugin

Add InspeKtor to your `build.gradle.kts`:

```kotlin title="build.gradle.kts"
plugins {
    kotlin("jvm") version "2.3.0"
    id("io.ktor.plugin") version "3.0.0"
    id("io.github.tabilzad.inspektor") version "0.10.0-alpha"
}

swagger {
    documentation {
        info {
            title = "My Ktor API"
            description = "A sample API built with Ktor"
            version = "1.0.0"
        }
    }
}
```

## Step 2: Define Your Models

Create data classes for your request and response bodies:

```kotlin title="Models.kt"
data class User(
    val id: Long,
    val name: String,
    val email: String,
    val createdAt: String
)

data class CreateUserRequest(
    val name: String,
    val email: String
)

data class ErrorResponse(
    val code: Int,
    val message: String
)
```

## Step 3: Annotate Your Routes

Add the `@GenerateOpenApi` annotation to your routing function:

```kotlin title="Application.kt"
import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.annotations.Tag
import io.github.tabilzad.ktor.responds

@GenerateOpenApi
fun Application.module() {
    routing {
        usersRoutes()
    }
}

@Tag(["Users"])
fun Route.usersRoutes() {
    route("/api/v1/users") {

        @KtorDescription(
            summary = "List all users",
            description = "Returns a paginated list of all users in the system"
        )
        get {
            responds<List<User>>(HttpStatusCode.OK)
            // Your implementation
        }

        @KtorDescription(
            summary = "Create a new user",
            description = "Creates a new user with the provided information"
        )
        post {
            responds<User>(HttpStatusCode.Created)
            responds<ErrorResponse>(HttpStatusCode.BadRequest)

            val request = call.receive<CreateUserRequest>()
            // Your implementation
        }

        route("/{id}") {
            @KtorDescription(summary = "Get user by ID")
            get {
                responds<User>(HttpStatusCode.OK)
                responds<ErrorResponse>(HttpStatusCode.NotFound)
                // Your implementation
            }

            @KtorDescription(summary = "Delete user")
            delete {
                respondsNothing(HttpStatusCode.NoContent)
                responds<ErrorResponse>(HttpStatusCode.NotFound)
                // Your implementation
            }
        }
    }
}
```

## Step 4: Build Your Project

Run the Gradle build:

```bash
./gradlew build
```

## Step 5: View Your Spec

Your OpenAPI specification is now available at:

```
build/resources/main/openapi/openapi.yaml
```

??? example "Generated OpenAPI Specification"

    ```yaml
    openapi: "3.1.0"
    info:
      title: "My Ktor API"
      description: "A sample API built with Ktor"
      version: "1.0.0"
    paths:
      /api/v1/users:
        get:
          tags:
            - "Users"
          summary: "List all users"
          description: "Returns a paginated list of all users in the system"
          responses:
            "200":
              description: "OK"
              content:
                application/json:
                  schema:
                    type: array
                    items:
                      $ref: "#/components/schemas/User"
        post:
          tags:
            - "Users"
          summary: "Create a new user"
          description: "Creates a new user with the provided information"
          requestBody:
            required: true
            content:
              application/json:
                schema:
                  $ref: "#/components/schemas/CreateUserRequest"
          responses:
            "201":
              description: "Created"
              content:
                application/json:
                  schema:
                    $ref: "#/components/schemas/User"
            "400":
              description: "Bad Request"
              content:
                application/json:
                  schema:
                    $ref: "#/components/schemas/ErrorResponse"
      /api/v1/users/{id}:
        get:
          tags:
            - "Users"
          summary: "Get user by ID"
          parameters:
            - name: id
              in: path
              required: true
              schema:
                type: string
          responses:
            "200":
              description: "OK"
              content:
                application/json:
                  schema:
                    $ref: "#/components/schemas/User"
            "404":
              description: "Not Found"
              content:
                application/json:
                  schema:
                    $ref: "#/components/schemas/ErrorResponse"
        delete:
          tags:
            - "Users"
          summary: "Delete user"
          parameters:
            - name: id
              in: path
              required: true
              schema:
                type: string
          responses:
            "204":
              description: "No Content"
            "404":
              description: "Not Found"
              content:
                application/json:
                  schema:
                    $ref: "#/components/schemas/ErrorResponse"
    components:
      schemas:
        User:
          type: object
          required:
            - id
            - name
            - email
            - createdAt
          properties:
            id:
              type: integer
              format: int64
            name:
              type: string
            email:
              type: string
            createdAt:
              type: string
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
        ErrorResponse:
          type: object
          required:
            - code
            - message
          properties:
            code:
              type: integer
              format: int32
            message:
              type: string
    ```

## What's Next?

Now that you've generated your first spec, explore more features:

- [Adding Descriptions](../usage/descriptions.md) - Document your endpoints with summaries and descriptions
- [Response Schemas](../usage/responses.md) - Define response types and status codes
- [Tags & Organization](../usage/tags.md) - Group related endpoints
- [Security Schemes](../configuration/security-schemes.md) - Add authentication documentation
