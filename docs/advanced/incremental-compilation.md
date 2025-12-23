# Incremental Compilation

Optimize build performance with InspeKtor's regeneration modes.

## Overview

InspeKtor operates as a Kotlin compiler plugin, which means it participates in Gradle's incremental compilation.
However, OpenAPI generation has specific requirements that can conflict with incremental compilation's assumptions.

## The Challenge

When you modify a Kotlin file, Gradle's incremental compilation only recompiles the changed files and their dependents.
This creates a challenge for OpenAPI generation:

**Scenario:** You have routes in `UserRoutes.kt` that reference a model in `User.kt`. If you only change `User.kt`:

1. Kotlin recompiles `User.kt`
2. `UserRoutes.kt` is NOT recompiled (it hasn't changed)
3. InspeKtor only sees the changes from `User.kt`
4. The generated spec might be incomplete

## Regeneration Modes

InspeKtor provides three modes to handle this:

```kotlin title="build.gradle.kts"
swagger {
    pluginOptions {
        regenerationMode = "strict" // default
    }
}
```

### strict (Default)

**Always regenerates the complete specification.**

```kotlin
regenerationMode = "strict"
```

| Aspect      | Value                    |
|-------------|--------------------------|
| Build Speed | Slower                   |
| Correctness | Guaranteed               |
| Best For    | CI/CD, production builds |

How it works:

- Marks the OpenAPI task as never up-to-date
- Forces full regeneration on every build
- Disables incremental compilation benefits for this task

```kotlin
// Equivalent effect
task.outputs.upToDateWhen { false }
```

**Use when:**

- Running CI/CD pipelines
- Creating release builds
- Accuracy is more important than speed
- You're not sure which mode to use

### safe

**Tracks files with `@GenerateOpenApi` annotation and regenerates when they change.**

```kotlin
regenerationMode = "safe"
```

| Aspect      | Value              |
|-------------|--------------------|
| Build Speed | Faster than strict |
| Correctness | Usually correct    |
| Best For    | Local development  |

How it works:

- Scans for files containing `@GenerateOpenApi`
- Registers these files as task inputs
- Regenerates when annotated files change
- Trusts Kotlin's incremental compilation for other changes

**Limitations:**

- May miss changes in helper functions called from routes
- May miss changes in models defined in other modules

**Use when:**

- Developing locally
- You primarily edit route files directly
- You want faster builds without sacrificing too much accuracy

### fast

**Trusts Kotlin's incremental compilation completely.**

```kotlin
regenerationMode = "fast"
```

| Aspect      | Value             |
|-------------|-------------------|
| Build Speed | Fastest           |
| Correctness | May be incomplete |
| Best For    | Rapid prototyping |

How it works:

- No additional input tracking
- Uses whatever Kotlin provides during incremental builds
- May produce partial specs during incremental builds

**Use when:**

- Rapid prototyping
- You don't need accurate specs during development
- You'll run a full build before deployment

## Recommended Configuration

### Development vs CI

```kotlin title="build.gradle.kts"
swagger {
    pluginOptions {
        // Use strict in CI, safe locally
        regenerationMode = if (System.getenv("CI") != null) "strict" else "safe"
    }
}
```

### Property-Based Selection

```kotlin title="build.gradle.kts"
swagger {
    pluginOptions {
        regenerationMode = findProperty("openapi.mode")?.toString() ?: "strict"
    }
}
```

Run with different modes:

```bash
# Fast local builds
./gradlew build -Popenapi.mode=fast

# Safe local builds (default)
./gradlew build -Popenapi.mode=safe

# Strict builds (CI)
./gradlew build -Popenapi.mode=strict
```

### Build Type Based

```kotlin title="build.gradle.kts"
swagger {
    pluginOptions {
        regenerationMode = when {
            project.hasProperty("release") -> "strict"
            project.hasProperty("quick") -> "fast"
            else -> "safe"
        }
    }
}
```

## Understanding the Trade-offs

### When strict is Necessary

```kotlin
// Module A: Models
data class User(
    val id: Long,
    val name: String,
    val email: String  // Added later
)

// Module B: Routes
@GenerateOpenApi
fun Application.module() {
    get("/users/{id}") {
        responds<User>(HttpStatusCode.OK)
    }
}
```

If you add `email` to `User` in Module A:

- **strict**: Regenerates, includes `email`
- **safe**: Might not detect the change
- **fast**: Definitely won't detect the change

### When safe is Sufficient

```kotlin
// Same module: Routes and inline changes
@GenerateOpenApi
fun Application.module() {
    get("/users") {
        responds<List<User>>(HttpStatusCode.OK)
    }

    // Adding this new route
    get("/users/{id}") {
        responds<User>(HttpStatusCode.OK)
    }
}
```

If you add a new route in the same file:

- **safe**: Detects the change, regenerates
- **fast**: Depends on incremental compilation

## Performance Impact

Typical build times (varies by project size):

| Mode   | Cold Build | Incremental Build |
|--------|------------|-------------------|
| strict | 10s        | 10s               |
| safe   | 10s        | 3s                |
| fast   | 10s        | 1s                |

The difference becomes more significant in larger projects.

## Verifying Your Spec

Regardless of mode, verify your spec is complete:

```bash
# Full clean build
./gradlew clean build

# Compare specs
diff build/resources/main/openapi/openapi.yaml expected-spec.yaml
```

## CI/CD Configuration

Always use `strict` mode in CI:

```yaml title=".github/workflows/build.yml"
jobs:
  build:
    runs-on: ubuntu-latest
    env:
      CI: true  # Triggers strict mode if configured
    steps:
      - uses: actions/checkout@v4
      - name: Build
        run: ./gradlew build
```

Or explicitly:

```yaml
- name: Build
  run: ./gradlew build -Popenapi.mode=strict
```

## Troubleshooting

### Spec seems incomplete

1. Run a clean build: `./gradlew clean build`
2. Check your regeneration mode
3. Ensure `@GenerateOpenApi` is on the right function

### Builds are too slow

1. Switch to `safe` or `fast` mode locally
2. Use `strict` only in CI
3. Consider splitting into multiple modules

### Changes not reflected

1. Check that you're editing files with `@GenerateOpenApi`
2. Try `safe` mode instead of `fast`
3. Run `./gradlew clean build` to verify
