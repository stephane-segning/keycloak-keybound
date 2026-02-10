package com.ssegning.keycloak.keybound.examples.resource

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping
class HttpBinLikeController {
    companion object {
        private val log = LoggerFactory.getLogger(HttpBinLikeController::class.java)
    }

    @GetMapping("/health")
    fun health(): Map<String, String> {
        log.info("Resource server health check")
        return mapOf("status" to "ok")
    }

    @GetMapping("/get")
    fun get(request: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
        val headers = request.headerNames.toList().associateWith { name -> request.getHeader(name) }
        log.info("Resource server GET {}?{}", request.requestURI, request.queryString ?: "")
        return ResponseEntity.ok(
            mapOf(
                "method" to request.method,
                "path" to request.requestURI,
                "query" to request.queryString,
                "headers" to headers
            )
        )
    }
}
