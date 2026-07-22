package io.github.tabilzad.ktor.model

/**
 * Location where a contributor module's partial OpenAPI spec is stored inside its
 * resources/JAR. Aggregator modules discover these entries on their compile classpath,
 * so this path is a stable contract between plugin versions.
 */
object PartialSpecLocation {
    const val RESOURCE_PATH = "META-INF/inspektor"
    const val FILE_NAME = "openapi-partial.json"
    const val FULL_PATH = "$RESOURCE_PATH/$FILE_NAME"
}
