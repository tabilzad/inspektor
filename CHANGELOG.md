# Changelog

All notable changes to this project will be documented in this file.

## [0.8.7-alpha] - 2024-08-14

### Fixed
- **Critical Build Performance Issue**: Fixed incremental build caching that was broken in previous versions
  - Removed `task.outputs.upToDateWhen { false }` logic that forced recompilation on every build
  - This particularly affected the `compileTestKotlin` task, causing it to always recompile
  - Build performance improvement: Tasks now properly marked as `UP-TO-DATE` on subsequent builds
  - Significantly reduces build times for projects using the inspektor plugin

### Changed
- Simplified compilation task configuration to not interfere with Gradle's caching mechanism
- Improved directory creation logic to work without breaking incremental builds

## [0.8.6-alpha] - Previous version

- Initial alpha release with OpenAPI generation support