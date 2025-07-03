import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.mavenPublish.base) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.detekt) apply false
}

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
    dependencies {
        classpath(kotlin("gradle-plugin", version = libs.versions.kotlinVersion.get()))
    }
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")

    group = "io.github.tabilzad.inspektor"
    version = project.properties["VERSION_NAME"].toString()

    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
            freeCompilerArgs.add("-Xcontext-parameters")
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_11.toString()
        targetCompatibility = JavaVersion.VERSION_11.toString()
        javaCompiler.set(
            javaToolchains.compilerFor {
                languageVersion.set(JavaLanguageVersion.of(11))
            }
        )
    }

    plugins.withId(rootProject.libs.plugins.mavenPublish.base.get().pluginId) {
        configure<MavenPublishBaseExtension> {
            publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)
            coordinates(
                groupId = project.group.toString(),
                artifactId = project.properties["POM_ARTIFACT_ID"].toString(),
                version = project.version.toString()
            )
            // dont sign for mavenLocal
            if (!gradle.startParameter.taskNames.any { it.contains("mavenLocal", ignoreCase = true) }) {
                signAllPublications()
            }
            pom {
                name.set(project.name)
                description.set("Open API (Swagger) specification Generator for Ktor")
                url.set("https://github.com/tabilzad/inspektor")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("tabilzad")
                        email.set("tim.abilzade@gmail.com")
                        url.set("https://github.com/tabilzad")
                    }
                }
                scm {
                    url.set("https://github.com/tabilzad/inspektor")
                }
            }
        }
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }

    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

    configure<DetektExtension> {
        autoCorrect = true
        parallel = true
        toolVersion = rootProject.libs.versions.detekt.get()
        source.from(fileTree("src") {
            include("*/java/")
            include("*/kotlin/")
        })
        config.from("$rootDir/config/detekt/detekt.yaml")
        // baseline = file("$rootDir/config/detekt/baseline.xml")
    }
    dependencies {
        "detektPlugins"(rootProject.libs.detekt.formatting)
        "detektPlugins"(rootProject.libs.detekt.libraries)
    }
}

dependencies {
    implementation(kotlin("stdlib"))
}
