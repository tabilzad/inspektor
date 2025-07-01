pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven(url="https://oss.sonatype.org/content/repositories/snapshots/")
        google()
    }
}
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "ktor-docs"
include("ktor-docs-plugin-gradle")
include("create-plugin")
include("annotations")
include("common")
