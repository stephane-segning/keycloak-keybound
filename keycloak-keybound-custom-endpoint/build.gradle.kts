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

    annotationProcessor("com.google.auto.service", "auto-service", "1.1.1")
    compileOnly("com.google.auto.service", "auto-service", "1.1.1")

    implementation("org.keycloak", "keycloak-services", "26.5.2")
    implementation("org.keycloak", "keycloak-server-spi", "26.5.2")
    implementation("org.keycloak", "keycloak-server-spi-private", "26.5.2")

    implementation(project(":keycloak-keybound-core"))
}

tasks.test {
    useJUnitPlatform()
}
