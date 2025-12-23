# Compatibility

Version compatibility information for InspeKtor.

## Version Matrix

| InspeKtor | Kotlin | Ktor     | OpenAPI | Gradle |
|-----------|--------|----------|---------|--------|
| 0.10.x    | 2.3.0+ | 2.x, 3.x | 3.1.0   | 8.0+   |
| 0.9.x     | 2.1.0+ | 2.x, 3.x | 3.1.0   | 7.6+   |
| 0.8.x     | 2.0.0+ | 2.x      | 3.1.0   | 7.6+   |

## Kotlin Version

InspeKtor requires Kotlin 2.3.0 or later for the latest version.

```kotlin title="build.gradle.kts"
plugins {
    kotlin("jvm") version "2.3.0" // Required
    id("io.github.tabilzad.inspektor") version "0.10.0-alpha"
}
```

### Why Kotlin 2.x?

InspeKtor uses Kotlin's K2 compiler plugin API, which is only available in Kotlin 2.0+. The K2 compiler provides:

- Faster compilation
- Better plugin APIs
- Improved type resolution

### Checking Your Kotlin Version

```bash
./gradlew --version
```

Or in your project:

```kotlin
println(org.jetbrains.kotlin.config.KotlinCompilerVersion.VERSION)
```

## Ktor Version

InspeKtor supports both Ktor 2.x and 3.x.

### Ktor 3.x (Recommended)

```kotlin
dependencies {
    implementation("io.ktor:ktor-server-core:3.0.0")
    implementation("io.ktor:ktor-server-netty:3.0.0")
}
```

### Ktor 2.x

```kotlin
dependencies {
    implementation("io.ktor:ktor-server-core:2.3.12")
    implementation("io.ktor:ktor-server-netty:2.3.12")
}
```

### Feature Compatibility

| Feature            | Ktor 2.x | Ktor 3.x |
|--------------------|----------|----------|
| Basic routing      | ✅        | ✅        |
| Resources plugin   | ✅        | ✅        |
| Authentication     | ✅        | ✅        |
| ContentNegotiation | ✅        | ✅        |

## Gradle Version

InspeKtor requires Gradle 8.0 or later.

### Checking Gradle Version

```bash
./gradlew --version
```

### Upgrading Gradle

Update `gradle/wrapper/gradle-wrapper.properties`:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
```

Or run:

```bash
./gradlew wrapper --gradle-version 8.5
```

## Build Systems

### Gradle (Kotlin DSL)

Primary supported build system:

```kotlin title="build.gradle.kts"
plugins {
    id("io.github.tabilzad.inspektor") version "0.10.0-alpha"
}
```

### Gradle (Groovy DSL)

```groovy title="build.gradle"
plugins {
    id 'io.github.tabilzad.inspektor' version '0.10.0-alpha'
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

### Maven

Maven is not currently supported. Please use Gradle.

## Serialization Libraries

### kotlinx.serialization

Fully supported:

```kotlin
@Serializable
data class User(
    val id: Long,
    val name: String
)
```

`@SerialName` annotations are respected for discriminator values.

### com.squareup.moshi

```kotlin
@JsonClass(generateAdapter = true)
data class User(
    @Json(name = "id") val id: Long,
    val id: Long,
    val name: String
)
```
 No custom polymorphic factory support.

### Gradle Caching Issues

If you see the spec not updating after successful builds try running
```bash
./gradlew compileKotlin --rerun-tasks 
# or 
./gradlew my-ktor-module:compileReleaseKotlin --rerun-tasks 
```
