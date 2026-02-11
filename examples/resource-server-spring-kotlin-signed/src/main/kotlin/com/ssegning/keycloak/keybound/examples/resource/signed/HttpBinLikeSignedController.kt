package com.ssegning.keycloak.keybound.examples.resource.signed

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping
class HttpBinLikeSignedController {
    companion object {
        private val log = LoggerFactory.getLogger(HttpBinLikeSignedController::class.java)
    }

    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf("status" to "ok")

    @GetMapping("/get")
    fun get(request: HttpServletRequest, authentication: Authentication): ResponseEntity<Map<String, Any?>> {
        return ResponseEntity.ok(buildHttpbinResponse(request, authentication, body = null))
    }

    @RequestMapping(
        value = ["/anything", "/anything/**"],
        method = [
            RequestMethod.GET,
            RequestMethod.POST,
            RequestMethod.PUT,
            RequestMethod.PATCH,
            RequestMethod.DELETE,
            RequestMethod.OPTIONS,
            RequestMethod.HEAD
        ]
    )
    fun anything(
        request: HttpServletRequest,
        authentication: Authentication,
        @RequestBody(required = false) body: String?
    ): ResponseEntity<Map<String, Any?>> {
        return ResponseEntity.ok(buildHttpbinResponse(request, authentication, body))
    }

    private fun buildHttpbinResponse(
        request: HttpServletRequest,
        authentication: Authentication,
        body: String?
    ): Map<String, Any?> {
        val headers = request.headerNames.toList().associateWith { headerName -> request.getHeader(headerName) }
        val jwt = authentication.principal as? Jwt
        val cnf = jwt?.claims?.get("cnf")

        log.info(
            "Signed resource {} {} sub={} jkt={}",
            request.method,
            request.requestURI,
            jwt?.subject,
            (cnf as? Map<*, *>)?.get("jkt")
        )

        return mapOf(
            "method" to request.method,
            "path" to request.requestURI,
            "query" to request.queryString,
            "headers" to headers,
            "body" to body,
            "auth" to mapOf(
                "authenticated" to authentication.isAuthenticated,
                "authorities" to authentication.authorities.map { it.authority }
            ),
            "keycloak" to mapOf(
                "subject" to jwt?.subject,
                "issuer" to jwt?.issuer?.toString(),
                "audience" to jwt?.audience.orEmpty(),
                "client_id" to jwt?.getClaimAsString("azp"),
                "scope" to jwt?.getClaimAsString("scope"),
                "cnf" to cnf,
                "issued_at" to jwt?.issuedAt?.toString(),
                "expires_at" to jwt?.expiresAt?.toString()
            )
        )
    }
}
