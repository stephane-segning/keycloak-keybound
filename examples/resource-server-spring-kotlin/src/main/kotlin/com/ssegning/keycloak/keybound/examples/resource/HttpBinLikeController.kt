package com.ssegning.keycloak.keybound.examples.resource

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping
class HttpBinLikeController(
    private val backendApprovalsClient: BackendApprovalsClient,
    private val backendUserIdResolver: BackendUserIdResolver
) {
    companion object {
        private val log = LoggerFactory.getLogger(HttpBinLikeController::class.java)
    }

    @GetMapping("/health")
    fun health(): Map<String, String> {
        log.info("Resource server health check")
        return mapOf("status" to "ok")
    }

    @GetMapping("/get")
    fun get(request: HttpServletRequest, authentication: Authentication): ResponseEntity<Map<String, Any?>> {
        val headers = request.headerNames.toList().associateWith { name -> request.getHeader(name) }
        val jwt = authentication.principal as? Jwt
        val subject = jwt?.subject
        val clientId = jwt?.getClaimAsString("azp")
        log.info(
            "Resource server GET {}?{} sub={} client={}",
            request.requestURI,
            request.queryString ?: "",
            subject,
            clientId
        )
        return ResponseEntity.ok(
            mapOf(
                "method" to request.method,
                "path" to request.requestURI,
                "query" to request.queryString,
                "headers" to headers,
                "auth" to mapOf(
                    "authenticated" to authentication.isAuthenticated,
                    "authorities" to authentication.authorities.map { it.authority }
                ),
                "keycloak" to mapOf(
                    "subject" to subject,
                    "issuer" to jwt?.issuer?.toString(),
                    "audience" to jwt?.audience.orEmpty(),
                    "client_id" to clientId,
                    "preferred_username" to jwt?.getClaimAsString("preferred_username"),
                    "scope" to jwt?.getClaimAsString("scope"),
                    "device_id" to jwt?.claims?.get("device_id"),
                    "cnf" to jwt?.claims?.get("cnf"),
                    "issued_at" to jwt?.issuedAt?.toString(),
                    "expires_at" to jwt?.expiresAt?.toString()
                )
            )
        )
    }

    @GetMapping("/approvals")
    fun approvals(authentication: Authentication): ResponseEntity<Map<String, Any?>> {
        val jwt = authentication.principal as? Jwt
        val backendUserId = backendUserIdResolver.resolve(jwt)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to resolve backend user id from token")

        val backendResponse = try {
            backendApprovalsClient.listUserApprovals(backendUserId)
        } catch (exception: Exception) {
            log.error("Failed to fetch approvals for backend user {}", backendUserId, exception)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Backend approvals request failed")
        }

        log.info("Fetched approvals for backend user {} through resource server", backendUserId)
        return ResponseEntity.ok(
            mapOf(
                "keycloak_subject" to jwt?.subject,
                "backend_user_id" to backendUserId,
                "backend" to backendResponse
            )
        )
    }

    @PostMapping("/approvals/{requestId}/approve")
    fun approveApproval(
        @PathVariable requestId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any?>> {
        val jwt = authentication.principal as? Jwt
        val backendUserId = backendUserIdResolver.resolve(jwt)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to resolve backend user id from token")

        val userApprovals = try {
            backendApprovalsClient.listUserApprovals(backendUserId)
        } catch (exception: Exception) {
            log.error("Failed to load approvals for backend user {} before approving {}", backendUserId, requestId, exception)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Backend approvals request failed")
        }
        if (!hasPendingApproval(userApprovals, requestId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Pending approval request not found for user")
        }

        val backendResponse = try {
            backendApprovalsClient.approveApproval(requestId)
        } catch (exception: Exception) {
            log.error("Failed to approve request {} for backend user {}", requestId, backendUserId, exception)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Backend approval decision request failed")
        }

        log.info("Approved request {} for backend user {}", requestId, backendUserId)
        return ResponseEntity.ok(
            mapOf(
                "keycloak_subject" to jwt?.subject,
                "backend_user_id" to backendUserId,
                "backend" to backendResponse
            )
        )
    }

    private fun hasPendingApproval(response: Map<String, Any?>, requestId: String): Boolean {
        val approvals = response["approvals"] as? List<*> ?: return false
        return approvals.asSequence()
            .mapNotNull { it as? Map<*, *> }
            .any { item ->
                (item["request_id"] as? String) == requestId && (item["status"] as? String) == "PENDING"
            }
    }
}
