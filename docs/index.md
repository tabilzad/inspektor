---
title: InspeKtor - OpenAPI Generator for Ktor
description: Generate OpenAPI (Swagger) specifications from your Ktor server code at build time
---

<style>
.md-typeset h1 {
  display: none;
}
</style>

<p align="center">
  <img src="assets/logo.png" alt="InspeKtor Logo" width="200">
</p>

<h1 align="center">InspeKtor</h1>

<p align="center">
  <strong>OpenAPI (Swagger) specification generator for Ktor</strong>
</p>

<p align="center">
  <a href="https://github.com/tabilzad/inspektor/actions/workflows/gradle-publish.yml">
    <img src="https://github.com/tabilzad/inspektor/actions/workflows/gradle-publish.yml/badge.svg" alt="Build Status">
  </a>
  <a href="https://central.sonatype.com/artifact/io.github.tabilzad.inspektor/ktor-docs-plugin">
    <img src="https://img.shields.io/maven-central/v/io.github.tabilzad.inspektor/ktor-docs-plugin?color=blue" alt="Maven Central">
  </a>
  <a href="https://kotlinlang.org">
    <img src="https://img.shields.io/badge/kotlin-2.3.0-blue.svg?logo=kotlin" alt="Kotlin">
  </a>
  <a href="https://github.com/tabilzad/inspektor/blob/master/LICENSE">
    <img src="https://img.shields.io/badge/License-Apache%202.0-green.svg" alt="License">
  </a>
</p>

---

## What is InspeKtor?

InspeKtor is a **Kotlin compiler plugin** that automatically generates OpenAPI (Swagger) specifications from your Ktor
server code at **build time**.

- **Zero runtime overhead** - All generation happens during compilation
- **No code modifications** - Just add an annotation to your existing routes
- **No special DSL** - Use your existing Ktor routing code
- **Always in sync** - Spec is generated from source, never drifts

## Quick Example

```kotlin title="Application.kt"
@GenerateOpenApi
fun Application.module() {
    routing {
        get("/hello") {
            call.respondText("Hello, World!")
        }

        post("/users") {
            val user = call.receive<CreateUserRequest>()
            call.respond(HttpStatusCode.Created, user)
        }
    }
}

data class CreateUserRequest(val name: String, val email: String)
```

Run `./gradlew build` and get a complete OpenAPI specification:

```yaml title="openapi.yaml"
openapi: "3.1.0"
paths:
  /hello:
    get:
      responses:
        "200":
          description: "OK"
  /users:
    post:
      requestBody:
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/CreateUserRequest"
      responses:
        "200":
          description: "OK"
components:
  schemas:
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
```

## Why InspeKtor?

|                    | Traditional Approach                        | InspeKtor              |
|--------------------|---------------------------------------------|------------------------|
| **Setup**          | Manual spec writing or complex DSL wrappers | Single annotation      |
| **Maintenance**    | Spec drifts from code over time             | Always in sync         |
| **Performance**    | Runtime overhead for spec generation        | Zero runtime cost      |
| **Learning Curve** | Learn new DSL or spec format                | Use existing Ktor code |

## Features

<div class="grid cards" markdown>

- :material-lightning-bolt:{ .lg .middle } **Automatic Detection**

    ---

  Automatically extracts endpoints, request bodies, and path parameters from your Ktor routing code.

- :material-shield-check:{ .lg .middle } **Type Safe**

    ---

  Full support for Kotlin types including sealed classes, value classes, generics, and nullability.

- :material-file-document:{ .lg .middle } **Rich Documentation**

    ---

  Add descriptions via annotations or extract them automatically from KDoc comments.

- :material-tag:{ .lg .middle } **Organization**

    ---

  Group endpoints with tags, define security schemes, and customize the output format.

</div>

## Getting Started

Ready to get started? Head over to the [Installation Guide](getting-started/installation.md) or jump straight to
the [Quick Start](getting-started/quick-start.md).

<div class="grid cards" markdown>

- [:material-download: **Installation**](getting-started/installation.md)

  Add InspeKtor to your Gradle project

- [:material-rocket-launch: **Quick Start**](getting-started/quick-start.md)

  Generate your first OpenAPI spec in 5 minutes

- [:material-github: **Sample Project**](https://github.com/tabilzad/ktor-inspektor-example)

  Complete working example on GitHub

</div>
