plugins {
    kotlin("jvm")
    id("org.openapi.generator") version "7.19.0"
}

group = "com.ssegning.keycloak.keybound"
version = "0.1.3"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation(project(":keycloak-keybound-core"))

    implementation("org.keycloak", "keycloak-services", "26.5.2")

    implementation("com.fasterxml.jackson.core", "jackson-databind", "2.21.0")
    implementation("com.fasterxml.jackson.module", "jackson-module-kotlin", "2.21.0")
    implementation("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310", "2.21.0")

    implementation("com.squareup.okhttp3", "okhttp", "4.12.0")
    implementation("com.squareup.okio", "okio-jvm", "3.10.2")
    implementation("org.slf4j", "slf4j-log4j12", "2.0.17")
    implementation("io.github.resilience4j", "resilience4j-circuitbreaker", "2.3.0")
    implementation("io.github.resilience4j", "resilience4j-retry", "2.3.0")

    testImplementation("io.kotlintest", "kotlintest-runner-junit5", "3.4.2")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

val generatedSourcesDir = "${layout.buildDirectory.asFile.get()}/generated/openapi"
val openapiPackageName = "com.ssegning.keycloak.keybound.api.openapi.client"

openApiGenerate {
    generatorName.set("kotlin")

    inputSpec.set("$rootDir/openapi/backend.open-api.yml")
    outputDir.set(generatedSourcesDir)

    packageName.set(openapiPackageName)
    apiPackage.set("$openapiPackageName.handler")
    invokerPackage.set("$openapiPackageName.invoker")
    modelPackage.set("$openapiPackageName.model")

    httpUserAgent.set("Keycloak/Kotlin")

    configOptions.set(
        mutableMapOf(
            "dateLibrary" to "java8-localdatetime",
            "serializationLibrary" to "jackson",
        ),
    )
}

sourceSets {
    getByName("main") {
        kotlin {
            srcDir("$generatedSourcesDir/src/main/kotlin")
        }
    }
    getByName("test") {
        kotlin {
            srcDir("$generatedSourcesDir/src/test/kotlin")
        }
    }
}

tasks {
    val openApiGenerate by getting

    val compileKotlin by getting {
        dependsOn(openApiGenerate)
    }

    matching { it.name.startsWith("runKtlint") }.configureEach {
        dependsOn(openApiGenerate)
    }
}
