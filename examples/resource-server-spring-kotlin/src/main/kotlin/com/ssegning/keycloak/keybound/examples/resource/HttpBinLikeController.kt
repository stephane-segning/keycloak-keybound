package com.ssegning.keycloak.keybound.examples.resource

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping
class HttpBinLikeController {
    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf("status" to "ok")

    @GetMapping("/get")
    fun get(request: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
        return ResponseEntity.ok(
            mapOf(
                "method" to request.method,
                "path" to request.requestURI,
                "query" to request.queryString,
                "headers" to request.headerNames.toList().associateWith { name -> request.getHeader(name) }
            )
        )
    }
}
