# API Reference

Complete reference for InspeKtor's annotations, functions, and Gradle DSL.

## Quick Links

<div class="grid cards" markdown>

- :material-at: **[Annotations](annotations.md)**

  `@GenerateOpenApi`, `@KtorDescription`, `@Tag`, and more

- :material-function: **[DSL Functions](dsl-functions.md)**

  `responds`, `respondsNothing`, and response documentation helpers

- :material-gradle: **[Gradle DSL](gradle-dsl.md)**

  Complete `swagger { }` block configuration reference

</div>

## Import Statements

```kotlin
// Annotations
import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.annotations.Tag

// DSL Functions
import io.github.tabilzad.ktor.responds
import io.github.tabilzad.ktor.respondsNothing
```

## At a Glance

### Annotations

| Annotation         | Purpose                                      |
|--------------------|----------------------------------------------|
| `@GenerateOpenApi` | Enable OpenAPI generation for a module/route |
| `@KtorDescription` | Add summary and description to endpoints     |
| `@Tag`             | Group endpoints under tags                   |

### DSL Functions

| Function                           | Purpose                                   |
|------------------------------------|-------------------------------------------|
| `responds<T>(status)`              | Document response type and status code    |
| `responds<T>(status, description)` | Document response with custom description |
| `respondsNothing(status)`          | Document response with no body            |

### Gradle Configuration

| Block                 | Purpose                         |
|-----------------------|---------------------------------|
| `swagger { }`         | Root configuration block        |
| `documentation { }`   | API metadata and schema options |
| `pluginOptions { }`   | Plugin behavior settings        |
| `security { }`        | Authentication configuration    |
| `serialOverrides { }` | Type mappings                   |
