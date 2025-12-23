---
title: InspeKtor - OpenAPI Generator for Ktor
description: Generate OpenAPI (Swagger) specifications from your Ktor server code at build time
hide:
  - navigation
  - toc
---

<div class="hero-section" markdown>

# InspeKtor

<p class="subtitle">
Generate OpenAPI specifications from your Ktor code at compile time.<br>
Zero runtime overhead. Full type safety. Always in sync.
</p>

<p align="center" style="margin-top: 2rem;">
  <a href="https://github.com/tabilzad/ktor-docs/actions/workflows/gradle-publish.yml">
    <img src="https://github.com/tabilzad/ktor-docs/actions/workflows/gradle-publish.yml/badge.svg" alt="Build Status">
  </a>
  <a href="https://central.sonatype.com/artifact/io.github.tabilzad/ktor-docs-plugin-gradle">
    <img src="https://img.shields.io/maven-central/v/io.github.tabilzad/ktor-docs-plugin-gradle?color=blue" alt="Maven Central">
  </a>
  <a href="https://kotlinlang.org">
    <img src="https://img.shields.io/badge/kotlin-2.3.0-blue.svg?logo=kotlin" alt="Kotlin">
  </a>
  <a href="https://github.com/tabilzad/ktor-docs/blob/master/LICENSE">
    <img src="https://img.shields.io/badge/License-Apache%202.0-green.svg" alt="License">
  </a>
</p>

[Get Started :material-arrow-right:](getting-started/index.md){ .md-button .md-button--primary }
[View on GitHub :material-github:](https://github.com/tabilzad/ktor-docs){ .md-button }

</div>

---

## Why InspeKtor?

<div class="grid cards" markdown>

-   :material-lightning-bolt:{ .lg .middle } **Zero Runtime Overhead**

    ---

    Documentation is generated at compile time, not runtime. Your application performance stays completely unaffected.

-   :material-shield-check:{ .lg .middle } **Type-Safe**

    ---

    Leverages Kotlin's type system to generate accurate schemas. Supports sealed classes, generics, and nullability.

-   :material-code-tags:{ .lg .middle } **Minimal Annotations**

    ---

    Most information is inferred from your code. Add annotations only when you need extra documentation.

-   :material-sync:{ .lg .middle } **Always In Sync**

    ---

    Your documentation is generated from actual code, so it's always accurate and never drifts out of date.

</div>

---

## Quick Start

### 1. Add the Plugin

```kotlin title="build.gradle.kts"
plugins {
    kotlin("jvm") version "2.3.0"
    id("io.ktor.plugin") version "3.0.0"
    id("io.github.tabilzad.inspektor") version "0.10.0-alpha"
}

swagger {
    documentation {
        info {
            title = "My API"
            version = "1.0.0"
        }
    }
}
```

### 2. Annotate Your Routes

```kotlin title="Application.kt"
@GenerateOpenApi
fun Application.module() {
    routing {
        route("/api/users") {

            @KtorDescription(summary = "List all users")
            get {
                responds<List<User>>(HttpStatusCode.OK)
                // Implementation
            }

            @KtorDescription(summary = "Create user")
            post {
                responds<User>(HttpStatusCode.Created)
                responds<ErrorResponse>(HttpStatusCode.BadRequest)
                val request = call.receive<CreateUserRequest>()
                // Implementation
            }
        }
    }
}
```

### 3. Build

```bash
./gradlew build
```

Your OpenAPI spec is generated at `build/resources/main/openapi/openapi.yaml` :material-check-circle:{ .success }

---

## Features at a Glance

| Feature | Description |
|---------|-------------|
| **Route Detection** | Automatically detects routes from Ktor's routing DSL |
| **Schema Generation** | Generates schemas from Kotlin data classes |
| **Request Bodies** | Infers request schemas from `call.receive<T>()` |
| **Response Types** | Documents responses with the `responds<T>()` function |
| **Path Parameters** | Detects parameters from `{param}` syntax |
| **Query Parameters** | Extracts query parameters from code |
| **KDoc Integration** | Extracts descriptions from KDoc comments |
| **Security Schemes** | Supports JWT, API Key, OAuth2, and more |
| **Sealed Classes** | Full support for polymorphic types with discriminators |
| **Generic Types** | Handles generic wrappers like `Response<T>` |
| **Incremental Builds** | Configurable regeneration modes for fast builds |

---

## Explore the Docs

<div class="grid cards" markdown>

-   :material-rocket-launch:{ .lg .middle } **Getting Started**

    ---

    Install InspeKtor and generate your first API spec in minutes.

    [:octicons-arrow-right-24: Installation](getting-started/installation.md)

-   :material-cog:{ .lg .middle } **Configuration**

    ---

    Customize API info, security schemes, type mappings, and more.

    [:octicons-arrow-right-24: Configuration](configuration/index.md)

-   :material-book-open-variant:{ .lg .middle } **Usage Guide**

    ---

    Learn how to document endpoints, responses, and parameters.

    [:octicons-arrow-right-24: Usage Guide](usage/index.md)

-   :material-code-braces:{ .lg .middle } **API Reference**

    ---

    Complete reference for annotations, functions, and Gradle DSL.

    [:octicons-arrow-right-24: API Reference](api/index.md)

</div>
