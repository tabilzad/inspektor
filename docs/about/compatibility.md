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

## Java Version

InspeKtor supports Java 11 and later.

```kotlin title="build.gradle.kts"
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
```

### Recommended Java Versions

| Java Version | Status                 |
|--------------|------------------------|
| Java 11      | Supported (minimum)    |
| Java 17      | Recommended (LTS)      |
| Java 21      | Supported (latest LTS) |

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

## IDE Support

### IntelliJ IDEA

InspeKtor works with IntelliJ IDEA 2023.1 and later.

For best experience:

- Use IntelliJ IDEA 2024.1+
- Enable the K2 Kotlin plugin
- Import as a Gradle project

### VS Code

Works with the Kotlin extension, but IntelliJ IDEA provides better support for Kotlin compiler plugins.

### Other IDEs

Any IDE that supports Gradle and Kotlin should work, though with varying levels of support for compiler plugin features.

## Operating Systems

InspeKtor works on all major operating systems:

- **Linux** (tested on Ubuntu, Debian, CentOS)
- **macOS** (Intel and Apple Silicon)
- **Windows** (Windows 10/11)

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

### Jackson

Supported for basic use cases:

```kotlin
data class User(
    @JsonProperty("user_id")
    val id: Long,
    val name: String
)
```

### Gson

Basic support. Consider using kotlinx.serialization for best results.

## Known Limitations

### Current Limitations

1. **No Maven support** - Gradle only
2. **No multiplatform** - JVM only
3. **Limited annotation processing** - Some advanced annotations not supported

### Planned Improvements

- Maven plugin support
- Enhanced annotation support
- More serialization library integrations

## Troubleshooting

### Plugin Not Found

Ensure you're using the correct plugin ID:

```kotlin
plugins {
    id("io.github.tabilzad.inspektor") version "0.10.0-alpha"
}
```

### Kotlin Version Mismatch

If you see errors about Kotlin version:

1. Check your Kotlin version matches the compatibility matrix
2. Run `./gradlew --refresh-dependencies`
3. Clean and rebuild: `./gradlew clean build`

### Gradle Version Issues

If you see Gradle-related errors:

1. Update Gradle to 8.0+
2. Check `gradle-wrapper.properties`
3. Run `./gradlew wrapper --gradle-version 8.5`
