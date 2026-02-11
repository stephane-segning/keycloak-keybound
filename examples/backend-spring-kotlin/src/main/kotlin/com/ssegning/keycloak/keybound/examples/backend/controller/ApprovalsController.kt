package com.ssegning.keycloak.keybound.examples.backend.controller

import com.ssegning.keycloak.keybound.examples.backend.api.ApprovalsApi
import com.ssegning.keycloak.keybound.examples.backend.model.ApprovalDecisionRequest
import com.ssegning.keycloak.keybound.examples.backend.model.ApprovalCreateRequest
import com.ssegning.keycloak.keybound.examples.backend.model.ApprovalCreateResponse
import com.ssegning.keycloak.keybound.examples.backend.model.ApprovalStatusResponse
import com.ssegning.keycloak.keybound.examples.backend.model.UserApprovalsResponse
import com.ssegning.keycloak.keybound.examples.backend.store.BackendDataStore
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class ApprovalsController(private val store: BackendDataStore) : ApprovalsApi {
    override fun createApproval(
        approvalCreateRequest: ApprovalCreateRequest,
        idempotencyKey: Any?
    ): ResponseEntity<ApprovalCreateResponse> {
        log.info(
            "Creating approval request for user {} device {}",
            approvalCreateRequest.userId,
            approvalCreateRequest.newDevice?.deviceId
        )
        val response = store.createApproval(approvalCreateRequest)
        log.debug("Created approval {}", response.requestId)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    override fun getApproval(requestId: String): ResponseEntity<ApprovalStatusResponse> {
        log.info("Retrieving approval status {}", requestId)
        val response = store.getApproval(requestId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Approval request not found")
        log.debug("Approval {} status {}", requestId, response.status)
        return ResponseEntity.ok(response)
    }

    override fun decideApproval(
        requestId: String,
        approvalDecisionRequest: ApprovalDecisionRequest
    ): ResponseEntity<ApprovalStatusResponse> {
        log.info("Applying decision {} on approval {}", approvalDecisionRequest.decision, requestId)
        val response = store.decideApproval(requestId, approvalDecisionRequest)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Approval request not found")
        log.debug("Approval {} new status {}", requestId, response.status)
        return ResponseEntity.ok(response)
    }

    override fun cancelApproval(requestId: String): ResponseEntity<Void> {
        log.info("Cancelling approval {}", requestId)
        val removed = store.cancelApproval(requestId)
        if (!removed) {
            log.warn("Approval {} was not found to cancel", requestId)
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Approval request not found")
        }
        log.debug("Approval {} cancelled", requestId)
        return ResponseEntity.noContent().build()
    }

    override fun listUserApprovals(
        userId: String,
        status: MutableList<String>?
    ): ResponseEntity<UserApprovalsResponse> {
        log.info("Listing approvals for user {} status_filter={}", userId, status)
        val response = store.listUserApprovals(userId, status)
        return ResponseEntity.ok(response)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ApprovalsController::class.java)
    }
}
