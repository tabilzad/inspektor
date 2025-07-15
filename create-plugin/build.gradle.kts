import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.mavenPublish.base)
    alias(libs.plugins.mavenShadow)
    alias(libs.plugins.dokka)
}

dependencies {
    compileOnly(libs.kotlinCompiler)

    implementation(projects.annotations)
    shadow(projects.common){
        isTransitive = false
    }

    implementation(libs.bundles.jackson)
    implementation(libs.kotlinReflect)
    implementation(libs.moshi)
    implementation(libs.serialization)
    implementation(libs.serialization.json)
    implementation(libs.bundles.ktor)

    testImplementation(projects.common)
    testImplementation(libs.classGraph)
    testImplementation(libs.compilerTest)
    testImplementation(libs.bundles.ktor)
    testImplementation(libs.assertJ)
    testImplementation(platform(libs.junit))
    testImplementation(libs.junitJupiter)
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    configurations = listOf(project.configurations.shadow.get())
    dependencies {
        include(dependency(projects.common))
    }
}

mavenPublishing {
    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Dokka("dokkaHtml"),
            sourcesJar = true
        )
    )
}
