import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("kapt") version "2.3.10"
    id("org.jetbrains.changelog") version "2.5.0"
    id("com.gradleup.shadow") version "9.3.1"
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1" apply false
}

group = "com.ssegning.keycloak.keybound"
version = "0.1.5"

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
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    dependencies {
        implementation(kotlin("stdlib"))
    }

    extensions.configure<KtlintExtension> {
        ignoreFailures.set(true)
        filter {
            exclude("**/build/**")
            exclude("**/generated/**")
        }
    }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        parallel = true
        basePath = rootDir.absolutePath
        ignoreFailures = true
    }

    tasks.withType<Detekt>().configureEach {
        exclude("**/build/**")
        exclude("**/generated/**")
    }

    tasks.named("check") {
        dependsOn("detekt", "ktlintCheck")
    }
}

changelog {
    unreleasedTerm.set("next")
    groups.empty()
    repositoryUrl.set("https://github.com/stephane-segning/keycloak-keybound")
}
