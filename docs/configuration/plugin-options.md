# Plugin Options

These options control the plugin's behavior, including output format, file location, and build performance.

## Basic Options

```kotlin title="build.gradle.kts"
swagger {
    pluginOptions {
        enabled = true
        saveInBuild = true
        format = "yaml"
        filePath = null
        regenerationMode = "strict"
    }
}
```

## Option Reference

### enabled

Enables or disables the plugin entirely.

```kotlin
enabled = true // default
```

Set to `false` to temporarily disable OpenAPI generation without removing the plugin:

```kotlin
pluginOptions {
    enabled = false // No spec will be generated
}
```

!!! tip "Conditional Enabling"
    You can enable/disable based on build type:
    ```kotlin
    enabled = !project.hasProperty("skipOpenApi")
    ```
    Then run: `./gradlew build -PskipOpenApi`

### saveInBuild

Controls whether the spec is saved in the `build/` directory.

```kotlin
saveInBuild = true // default
```

When `true`, the spec is saved to:
```
build/resources/main/openapi/openapi.yaml
```

When `false` and `filePath` is not set, it saves to:
```
src/main/resources/openapi/openapi.yaml
```

### format

Sets the output format for the generated specification.

```kotlin
format = "yaml" // default
```

| Value | Description |
|-------|-------------|
| `"yaml"` | YAML format (default, more readable) |
| `"json"` | JSON format (better for programmatic use) |

### filePath

Custom absolute path for the generated specification.

```kotlin
filePath = null // default - uses saveInBuild setting
```

Override to save to a specific location:

```kotlin
pluginOptions {
    filePath = "${project.projectDir}/docs/api/openapi.yaml"
}
```

!!! warning "Absolute Path Required"
    The `filePath` must be an absolute path. Use `${project.projectDir}` to reference your project directory.

### regenerationMode

Controls how the plugin handles incremental compilation.

```kotlin
regenerationMode = "strict" // default
```

| Mode | Build Speed | Correctness | Best For |
|------|-------------|-------------|----------|
| `"strict"` | Slower | Always complete | CI/CD, releases |
| `"safe"` | Balanced | Usually complete | Local development |
| `"fast"` | Fastest | May be incomplete | Rapid prototyping |

#### strict (Default)

Always regenerates the full spec on every build:

```kotlin
regenerationMode = "strict"
```

**Pros:** Guarantees a complete and accurate specification

**Cons:** Disables incremental compilation benefits

**Use for:** CI/CD pipelines, production builds, any build where spec accuracy is critical

#### safe

Tracks source files containing `@GenerateOpenApi` and regenerates when they change:

```kotlin
regenerationMode = "safe"
```

**Pros:** Faster builds when editing non-route files

**Cons:** May miss body-only changes in route helper functions

**Use for:** Local development when you primarily edit `@GenerateOpenApi` annotated files

#### fast

Trusts Kotlin's incremental compilation completely:

```kotlin
regenerationMode = "fast"
```

**Pros:** Fastest possible builds

**Cons:** May produce incomplete specs during incremental builds

**Use for:** Rapid prototyping where you don't need an accurate spec

!!! note "CI/CD Recommendation"
    For CI/CD pipelines, always use `strict` mode to ensure your deployed API documentation is complete.

## Environment-Based Configuration

Configure different options for different environments:

```kotlin title="build.gradle.kts"
swagger {
    pluginOptions {
        // Use fast mode locally, strict in CI
        regenerationMode = if (System.getenv("CI") != null) "strict" else "safe"

        // Different output format for different environments
        format = findProperty("openapi.format")?.toString() ?: "yaml"
    }
}
```

## Complete Reference

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `enabled` | `Boolean` | `true` | Enable/disable the plugin |
| `saveInBuild` | `Boolean` | `true` | Save spec in build directory |
| `format` | `String` | `"yaml"` | Output format: yaml or json |
| `filePath` | `String?` | `null` | Custom output path (absolute) |
| `regenerationMode` | `String` | `"strict"` | Incremental build mode |
