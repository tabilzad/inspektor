# Changelog

All notable changes to InspeKtor are documented here.

## [Unreleased]

### Added
- Documentation site with MkDocs Material theme
- Regeneration mode feature for incremental compilation control
- Memory leak fix in OpenApiSpecCollector

### Changed
- Updated Kotlin compiler to 2.3.0
- Improved path normalization for multi-module projects

## [0.10.0-alpha] - 2024

### Added
- Support for Kotlin 2.3.0
- `regenerationMode` configuration option with three modes:
  - `strict` - Always regenerates (default, best for CI)
  - `safe` - Tracks @GenerateOpenApi files
  - `fast` - Trusts incremental compilation
- Gradle task input/output tracking for better caching
- Support for merging specs from multiple `@GenerateOpenApi` annotations

### Changed
- Improved path cleaning for deeply nested routes
- Better handling of sealed class hierarchies
- Enhanced type resolution for generic types

### Fixed
- Memory leak in static OpenApiSpecCollector map
- Path duplication in multi-module projects
- Stack overflow with self-referencing sealed classes

## [0.9.0] - 2024

### Added
- Support for Ktor 3.0
- OpenAPI 3.1.0 output format
- Enhanced sealed class support with discriminator mapping
- `polymorphicDiscriminator` configuration option

### Changed
- Migrated to K2 compiler plugin API
- Improved schema generation for nullable types
- Better handling of default values

### Fixed
- Generic type resolution issues
- Nested route path construction

## [0.8.0] - 2023

### Added
- Initial public release
- Basic route detection from Ktor DSL
- Automatic schema generation from data classes
- `@GenerateOpenApi` annotation
- `@KtorDescription` annotation for endpoint documentation
- `@Tag` annotation for grouping
- `responds` and `respondsNothing` DSL functions
- Gradle configuration DSL
- Security scheme support
- Type override system

### Supported Features
- Path, query, and header parameter detection
- Request body inference from `call.receive<T>()`
- KDoc extraction for descriptions
- YAML and JSON output formats
- Configurable output location

---

## Version Numbering

InspeKtor follows [Semantic Versioning](https://semver.org/):

- **MAJOR**: Incompatible API changes
- **MINOR**: New features, backwards compatible
- **PATCH**: Bug fixes, backwards compatible

Alpha/Beta versions indicate pre-release status.

## Upgrade Guide

### Upgrading to 0.10.x

1. Update Kotlin to 2.3.0:
   ```kotlin
   plugins {
       kotlin("jvm") version "2.3.0"
   }
   ```

2. Update the plugin:
   ```kotlin
   plugins {
       id("io.github.tabilzad.inspektor") version "0.10.0-alpha"
   }
   ```

3. (Optional) Configure regeneration mode:
   ```kotlin
   swagger {
       pluginOptions {
           regenerationMode = "strict" // or "safe" or "fast"
       }
   }
   ```

### Upgrading to 0.9.x

1. Update Kotlin to 2.1.0+
2. Update the plugin version
3. Review sealed class schemas (discriminator handling changed)

## Reporting Issues

Found a bug? Please report it on [GitHub Issues](https://github.com/tabilzad/ktor-docs/issues).

Include:
- InspeKtor version
- Kotlin version
- Ktor version
- Minimal reproduction case
- Expected vs actual behavior
