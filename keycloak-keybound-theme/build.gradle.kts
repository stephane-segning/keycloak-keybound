plugins {
    kotlin("jvm")
}

group = "com.ssegning.keycloak.keybound"
version = "0.1.2"

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
