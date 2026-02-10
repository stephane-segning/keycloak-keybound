package com.ssegning.keycloak.keybound.examples.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BackendExampleApplication

fun main(args: Array<String>) {
    runApplication<BackendExampleApplication>(*args)
}
