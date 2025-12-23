# Installation

This guide covers how to add InspeKtor to your Ktor project.

## Gradle Setup

### Kotlin DSL (Recommended)

Add the plugin to your `build.gradle.kts`:

```kotlin title="build.gradle.kts"
plugins {
    kotlin("jvm") version "2.3.0"
    id("io.ktor.plugin") version "3.0.0"
    id("io.github.tabilzad.inspektor") version "0.10.0-alpha" // (1)!
}
```

1. Make sure to use the version compatible with your Kotlin version. See [Compatibility](../about/compatibility.md).

### Groovy DSL

```groovy title="build.gradle"
plugins {
    id 'org.jetbrains.kotlin.jvm' version '2.3.0'
    id 'io.ktor.plugin' version '3.0.0'
    id 'io.github.tabilzad.inspektor' version '0.10.0-alpha'
}
```

## Basic Configuration

Once the plugin is applied, you can configure it using the `swagger` block:

```kotlin title="build.gradle.kts"
swagger {
    documentation {
        info {
            title = "My API"
            description = "API documentation for my Ktor server"
            version = "1.0.0"
        }
    }

    pluginOptions {
        format = "yaml" // or "json"
    }
}
```

## Verify Installation

To verify the plugin is working:

1. Add the `@GenerateOpenApi` annotation to any route function:

    ```kotlin
    import io.github.tabilzad.ktor.annotations.GenerateOpenApi

    @GenerateOpenApi
    fun Application.module() {
        routing {
            get("/health") {
                call.respondText("OK")
            }
        }
    }
    ```

2. Build your project:

    ```bash
    ./gradlew build
    ```

3. Check for the generated spec at:
    - `build/resources/main/openapi/openapi.yaml` (default)
    - Or `build/resources/main/openapi/openapi.json` if using JSON format

## Multi-Module Projects

For multi-module projects, apply the plugin to each module that contains routes:

```kotlin title="api-module/build.gradle.kts"
plugins {
    id("io.github.tabilzad.inspektor")
}

swagger {
    documentation {
        info {
            title = "API Module"
            version = "1.0.0"
        }
    }
}
```

!!! tip "Shared Models"
If your request/response models are in a separate module, InspeKtor will still resolve them correctly as long as they're
on the classpath during compilation.

## Troubleshooting

### Plugin Not Found

If you see "Plugin not found" errors, make sure:

1. You're using a compatible Kotlin version (see [Compatibility](../about/compatibility.md))
2. The plugin is available in Maven Central (check your repository configuration)

```kotlin title="settings.gradle.kts"
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

### No Spec Generated

If no OpenAPI spec is generated:

1. Ensure at least one function is annotated with `@GenerateOpenApi`
2. Check that `pluginOptions.enabled` is not set to `false`
3. Look for compilation errors that might prevent the plugin from running

## Next Steps

Now that InspeKtor is installed, head to the [Quick Start](quick-start.md) guide to generate your first OpenAPI
specification!
