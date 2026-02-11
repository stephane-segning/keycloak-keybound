package com.ssegning.keycloak.keybound.examples.resource

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class BackendApprovalsClient(
    @Value("\${backend.base-url:http://localhost:18080}")
    private val backendBaseUrl: String
) {
    private val log = LoggerFactory.getLogger(BackendApprovalsClient::class.java)
    private val restClient = RestClient.builder()
        .baseUrl(backendBaseUrl)
        .build()

    fun listUserApprovals(userId: String): Map<String, Any?> {
        log.info("Calling backend approvals API for user {}", userId)
        val response = restClient.get()
            .uri("/v1/users/{user_id}/approvals", userId)
            .retrieve()
            .body(object : ParameterizedTypeReference<Map<String, Any?>>() {})

        return response ?: mapOf("user_id" to userId, "approvals" to emptyList<Any>())
    }

    fun approveApproval(requestId: String): Map<String, Any?> {
        log.info("Calling backend approval decision API for request {}", requestId)
        val response = restClient.post()
            .uri("/v1/approvals/{request_id}/decision", requestId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                mapOf(
                    "decision" to "APPROVE"
                )
            )
            .retrieve()
            .body(object : ParameterizedTypeReference<Map<String, Any?>>() {})

        return response ?: mapOf("request_id" to requestId, "status" to "UNKNOWN")
    }
}
