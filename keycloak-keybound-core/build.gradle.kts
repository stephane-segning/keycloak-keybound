import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
}

group = "com.ssegning.keycloak.keybound"
version = project.parent?.version ?: "latest"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    annotationProcessor("com.google.auto.service", "auto-service", "1.1.1")
    compileOnly("com.google.auto.service", "auto-service", "1.1.1")

    implementation("com.googlecode.libphonenumber", "libphonenumber", "9.0.20")
    implementation("com.squareup.okhttp3", "okhttp", "4.12.0")
    // Ensure kotlin-reflect matches the Kotlin compiler/stdlib version used by this build (avoid transitive mismatch).
    implementation(kotlin("reflect"))
    implementation("com.fasterxml.jackson.core", "jackson-databind", "2.21.0")
    implementation("com.fasterxml.jackson.module", "jackson-module-kotlin", "2.21.0")
    implementation("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310", "2.21.0")
    implementation("io.github.resilience4j", "resilience4j-circuitbreaker", "2.3.0")
    implementation("io.github.resilience4j", "resilience4j-retry", "2.3.0")

    implementation("org.keycloak", "keycloak-services", "26.5.2")
    implementation("org.keycloak", "keycloak-server-spi", "26.5.2")
    implementation("org.keycloak", "keycloak-server-spi-private", "26.5.2")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

val shadowJarTask = tasks.named<ShadowJar>("shadowJar")

shadow {
    addShadowVariantIntoJavaComponent = false
}

shadowJarTask.configure {
    archiveBaseName = "keycloak-keybound-all"

    dependencies {
        include(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
        include(dependency("org.jetbrains.kotlin:kotlin-reflect"))
        include(dependency("com.googlecode.libphonenumber:libphonenumber"))
        include(dependency("com.fasterxml.jackson.core:jackson-databind"))
        include(dependency("com.fasterxml.jackson.core:jackson-core"))
        include(dependency("com.fasterxml.jackson.core:jackson-annotations"))
        include(dependency("com.fasterxml.jackson.module:jackson-module-kotlin"))
        include(dependency("com.fasterxml.jackson.datatype:jackson-datatype-jsr310"))
        include(dependency("com.squareup.okhttp3:okhttp"))
        include(dependency("com.squareup.okio:okio-jvm"))
        include(dependency("io.github.resilience4j:resilience4j-circuitbreaker"))
        include(dependency("io.github.resilience4j:resilience4j-retry"))
        include(dependency("io.github.resilience4j:resilience4j-core"))
    }
}

tasks.named<Jar>("jar") {
    enabled = false
}

listOf("apiElements", "runtimeElements").forEach {
    configurations.named(it) {
        outgoing.artifact(shadowJarTask)
    }
}

tasks.named("build") {
    dependsOn(shadowJarTask)
}
