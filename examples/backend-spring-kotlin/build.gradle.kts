plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.openapi.generator") version "7.1.0"
}

group = "com.ssegning.keycloak.keybound.examples"
version = "0.1.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-freemarker")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.openapitools:jackson-databind-nullable:0.2.2")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("javax.validation:validation-api:2.0.1.Final")
    implementation("io.swagger.core.v3:swagger-annotations:2.2.12")
    implementation("jakarta.annotation:jakarta.annotation-api:2.1.1")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("javax.servlet:javax.servlet-api:4.0.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

openApiGenerate {
    generatorName.set("spring")
    library.set("spring-boot")
    inputSpec.set(file("${projectDir.parentFile.parentFile}/openapi/backend.open-api.yml").absolutePath)
    outputDir.set(layout.buildDirectory.dir("generated").get().asFile.absolutePath)
    apiPackage.set("com.ssegning.keycloak.keybound.examples.backend.api")
    modelPackage.set("com.ssegning.keycloak.keybound.examples.backend.model")
    invokerPackage.set("com.ssegning.keycloak.keybound.examples.backend.invoker")
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "dateLibrary" to "java8-localdatetime",
            "useTags" to "true"
        )
    )
}

sourceSets["main"].java {
    srcDir(layout.buildDirectory.dir("generated/src/main/java"))
}

tasks.named("compileKotlin") {
    dependsOn("openApiGenerate")
}

tasks.named("compileJava") {
    dependsOn("openApiGenerate")
}
