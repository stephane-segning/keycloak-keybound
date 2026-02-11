import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
}

group = "com.ssegning.keycloak.keybound"
version = "0.1.1"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    annotationProcessor("com.google.auto.service", "auto-service", "1.1.1")
    compileOnly("com.google.auto.service", "auto-service", "1.1.1")

    implementation("com.googlecode.libphonenumber", "libphonenumber", "9.0.20")
    implementation("com.squareup.okhttp3", "okhttp", "4.12.0")

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
        include(dependency("com.googlecode.libphonenumber:libphonenumber"))
        include(dependency("com.google.code.gson:gson"))
        include(dependency("com.squareup.okhttp3:okhttp"))
        include(dependency("com.squareup.okio:okio-jvm"))
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
