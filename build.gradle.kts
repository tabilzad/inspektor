import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.20"
}

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
    dependencies {
        classpath(kotlin("gradle-plugin", version = libs.versions.kotlinVersion.get()))
        classpath ("com.vanniktech:gradle-maven-publish-plugin:0.28.0")
        classpath ("org.jetbrains.dokka:dokka-gradle-plugin:1.5.30")
    }
}

subprojects {
    group = project.properties["GROUP"].toString()
    version =  project.properties["VERSION_NAME"].toString()
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
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
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

dependencies {
    implementation(kotlin("stdlib"))
}
