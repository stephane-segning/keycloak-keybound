import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.ssegning.keycloak.keybound"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation("com.googlecode.libphonenumber", "libphonenumber", "9.0.20")

    implementation("org.keycloak", "keycloak-services", "26.5.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks {
    val shadowJar by existing(ShadowJar::class) {
        dependencies {
            include(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
            include(dependency("com.googlecode.libphonenumber:libphonenumber"))
            include(dependency("com.google.code.gson:gson"))
        }
        dependsOn(build)
    }
}