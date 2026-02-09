plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("kapt") version "2.3.10"
    id("org.jetbrains.changelog") version "2.5.0"
}

group = "com.ssegning.keycloak.keybound"
version = "0.1.0"

repositories {
    mavenCentral()
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
