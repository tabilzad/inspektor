import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar

plugins {
    id("java-gradle-plugin")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.mavenPublish.base)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.mavenShadow)
}

dependencies {
    compileOnly(libs.bundles.kotlinGradle)
    compileOnly(gradleApi())

    shadow(projects.common){
        isTransitive = false
    }

    implementation(libs.serialization.json)
    implementation(libs.serialization)
    implementation(libs.semver)
}

gradlePlugin {
    plugins {
        create("gradlePlugin") {
            id = "io.github.tabilzad.inspektor"
            displayName = "Ktor Open API specification Generator"
            description = "Open API (Swagger) specification Generator for Ktor"
            implementationClass = "io.github.tabilzad.ktor.KtorMetaPlugin"
        }
    }
}

val versionDirectory = "${layout.buildDirectory.asFile.get().path}/generated/version/"

sourceSets {
    main {
        java.srcDir(versionDirectory)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    configurations = listOf(project.configurations.shadow.get())
    dependencies {
        include(dependency(projects.common))
    }
}

tasks {
    register("pluginVersion") {
        mustRunAfter("sourcesJar")
        val outputDir = file(versionDirectory)
        inputs.property("version", project.version)
        // Track expected Kotlin version from the version catalog so a rebuild updates the generated code
        inputs.property("kotlinVersion", libs.versions.kotlinVersion.get())
        outputs.dir(outputDir)
        doLast {
            val versionFile = file("$outputDir/io/github/tabilzad/ktor/version.kt")
            versionFile.parentFile.mkdirs()
            versionFile.writeText(
                """ | // Generated file. Do not edit!
                | package io.github.tabilzad.ktor
                |            
                | internal const val inspektorVersion = "${project.version}" 
                | internal const val kotlinVersion = "${libs.versions.kotlinVersion.get()}"
                """.trimMargin("| ")
            )
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn("pluginVersion")
}

mavenPublishing {
    configure(
        GradlePlugin(
            javadocJar = JavadocJar.Dokka("dokkaHtml"),
            sourcesJar = true
        )
    )
}

