package com.ssegning.keycloak.keybound.examples.resource.signed

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ResourceServerSignedExampleApplication

fun main(args: Array<String>) {
    runApplication<ResourceServerSignedExampleApplication>(*args)
}
