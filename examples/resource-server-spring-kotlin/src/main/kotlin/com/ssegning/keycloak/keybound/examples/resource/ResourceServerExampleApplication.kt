package com.ssegning.keycloak.keybound.examples.resource

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ResourceServerExampleApplication

fun main(args: Array<String>) {
    runApplication<ResourceServerExampleApplication>(*args)
}
