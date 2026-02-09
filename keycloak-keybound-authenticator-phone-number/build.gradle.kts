plugins {
    kotlin("jvm")
}

group = "com.ssegning.keycloak.keybound"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation("org.keycloak", "keycloak-services", "26.5.2")

    implementation(project(":keycloak-keybound-core"))
}

tasks.test {
    useJUnitPlatform()
}