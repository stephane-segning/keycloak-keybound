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

    implementation("com.googlecode.libphonenumber", "libphonenumber", "9.0.20")

    implementation("org.keycloak", "keycloak-services", "26.5.2")
}

tasks.test {
    useJUnitPlatform()
}