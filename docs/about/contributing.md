# Contributing

Thank you for your interest in contributing to InspeKtor!

## Ways to Contribute

### Report Bugs

Found a bug? [Open an issue](https://github.com/tabilzad/inspektor/issues/new) with:

- InspeKtor, Kotlin, and Ktor versions
- Minimal code to reproduce the issue
- Expected vs actual behavior
- Stack traces if applicable

### Suggest Features

Have an idea? [Open a feature request](https://github.com/tabilzad/inspektor/issues/new) with:

- Description of the feature
- Use case / motivation
- Example of how it would work

### Improve Documentation

Documentation improvements are always welcome:

- Fix typos or unclear explanations
- Add examples
- Improve the guides

### Submit Code

Want to fix a bug or add a feature?

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## Development Setup

### Prerequisites

- JDK 17+
- Gradle 8.0+
- IntelliJ IDEA (recommended)

### Clone the Repository

```bash
git clone https://github.com/tabilzad/inspektor.git
cd ktor-docs
```

### Build the Project

```bash
./gradlew build
```

### Run Tests

```bash
./gradlew test
```

### Run a Specific Test

```bash
./gradlew :create-plugin:test --tests "TestClassName.testMethodName"
```

## Project Structure

```
ktor-docs/
├── create-plugin/          # Kotlin compiler plugin
│   ├── src/main/kotlin/    # Plugin implementation
│   └── src/test/kotlin/    # Plugin tests
├── ktor-docs-plugin-gradle/ # Gradle plugin
│   └── src/main/kotlin/    # Gradle DSL
├── annotations/            # Annotation library
│   └── src/main/kotlin/    # @GenerateOpenApi, etc.
├── docs/                   # Documentation (MkDocs)
└── examples/               # Example projects
```

## Code Guidelines

### Kotlin Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful names
- Keep functions focused
- Add KDoc for public APIs

### Commit Messages

Use clear, descriptive commit messages:

```
feat: add support for nullable generic types

- Handle Optional<T> and T? in schema generation
- Add tests for nullable generics
- Update documentation
```

Prefixes:

- `feat:` - New feature
- `fix:` - Bug fix
- `docs:` - Documentation
- `test:` - Tests
- `refactor:` - Code refactoring
- `chore:` - Maintenance

### Pull Requests

1. **One feature per PR** - Keep PRs focused
2. **Add tests** - New features need tests
3. **Update docs** - If behavior changes, update documentation
4. **Pass CI** - All tests must pass

## Testing

### Unit Tests

Test individual components:

```kotlin
@Test
fun `should generate schema for data class`() {
    // Arrange
    val dataClass = ...

    // Act
    val schema = generateSchema(dataClass)

    // Assert
    assertEquals("object", schema.type)
}
```

### Integration Tests

Test the full plugin:

```kotlin
@Test
fun `should generate complete OpenAPI spec`() {
    // Compile test sources with plugin
    val result = compile(testSource)

    // Verify generated spec
    val spec = readGeneratedSpec()
    assertContains(spec.paths, "/users")
}
```

### Running Tests Locally

```bash
# All tests
./gradlew test

# Specific module
./gradlew :create-plugin:test

# With coverage
./gradlew test jacocoTestReport
```

## Documentation

### Building Docs Locally

```bash
# Install MkDocs
pip install mkdocs-material

# Serve locally
mkdocs serve

# Build
mkdocs build
```

### Documentation Structure

```
docs/
├── index.md                 # Home page
├── getting-started/         # Getting started guides
├── configuration/           # Configuration reference
├── usage/                   # Usage guides
├── advanced/                # Advanced topics
├── api/                     # API reference
└── about/                   # About pages
```

## Release Process

Releases are managed by maintainers:

1. Update version in `build.gradle.kts`
2. Update `CHANGELOG.md`
3. Create release branch
4. Run full test suite
5. Create GitHub release
6. Publish to Maven Central

## Getting Help

- [GitHub Discussions](https://github.com/tabilzad/inspektor/discussions) - Questions and discussions
- [GitHub Issues](https://github.com/tabilzad/inspektor/issues) - Bug reports and feature requests

## Code of Conduct

Be respectful and constructive. We're all here to build great software together.

## License

By contributing, you agree that your contributions will be licensed under the Apache 2.0 License.
