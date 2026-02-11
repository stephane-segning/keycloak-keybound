plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("kapt") version "2.3.10"
    id("org.jetbrains.changelog") version "2.5.0"
    id("com.gradleup.shadow") version "9.3.1"
}

group = "com.ssegning.keycloak.keybound"
version = "0.1.1"

repositories {
    mavenCentral()
}

tasks.named("jar") {
    enabled = false
}

tasks.named("shadowJar") {
    enabled = false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib"))
    }
}

changelog {
    unreleasedTerm.set("next")
    groups.empty()
    repositoryUrl.set("https://github.com/stephane-segning/keycloak-keybound")
}
