plugins {
    kotlin("jvm")
}

group = "com.ssegning.keycloak.keybound"
version = project.parent?.version ?: "latest"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
