# About InspeKtor

InspeKtor is a Kotlin compiler plugin that generates OpenAPI specifications from your Ktor routing code.

## Why InspeKtor?

### The Problem

Keeping API documentation in sync with code is hard:

- **Annotation-based tools** require verbose annotations that duplicate your code
- **Runtime tools** need your server running and may miss edge cases
- **Manual specs** quickly become outdated

### The Solution

InspeKtor analyzes your Ktor routing DSL at compile time and generates accurate OpenAPI documentation automatically. It:

- Reads your actual route definitions
- Infers request/response types from your code
- Extracts documentation from KDoc comments
- Generates the spec as part of your normal build

## Key Features

| Feature                 | Description                          |
|-------------------------|--------------------------------------|
| **Zero Runtime**        | No impact on application performance |
| **Type-Safe**           | Leverages Kotlin's type system       |
| **Minimal Annotations** | Most information is inferred         |
| **Incremental**         | Fast rebuilds during development     |
| **Configurable**        | Extensive Gradle DSL                 |

## How It Works

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Kotlin Code   │────▶│ Compiler Plugin  │────▶│  OpenAPI Spec   │
│                 │     │                  │     │                 │
│ • Routes        │     │ • Analyzes DSL   │     │ • paths         │
│ • Data classes  │     │ • Resolves types │     │ • schemas       │
│ • Annotations   │     │ • Extracts docs  │     │ • security      │
└─────────────────┘     └──────────────────┘     └─────────────────┘
```

1. You write Ktor routes normally
2. Add `@GenerateOpenApi` to your module function
3. Build your project
4. Get a complete OpenAPI specification

## Project Links

- [:material-github: GitHub Repository](https://github.com/tabilzad/ktor-docs)
- [:material-package-variant: Maven Central](https://central.sonatype.com/artifact/io.github.tabilzad/ktor-docs-plugin-gradle)
- [:material-bug: Issue Tracker](https://github.com/tabilzad/ktor-docs/issues)

## About This Documentation

This documentation is built with [MkDocs](https://www.mkdocs.org/) and
the [Material for MkDocs](https://squidfunk.github.io/mkdocs-material/) theme.

## Acknowledgments

InspeKtor is built on top of:

- [Kotlin Compiler Plugin API](https://kotlinlang.org/docs/compiler-plugins.html)
- [Ktor Framework](https://ktor.io/)
- [OpenAPI Specification](https://www.openapis.org/)
