package com.ssegning.keycloak.keybound.examples.backend.controller

import com.ssegning.keycloak.keybound.examples.backend.api.ApprovalsApi
import com.ssegning.keycloak.keybound.examples.backend.model.ApprovalCreateRequest
import com.ssegning.keycloak.keybound.examples.backend.model.ApprovalCreateResponse
import com.ssegning.keycloak.keybound.examples.backend.model.ApprovalStatusResponse
import com.ssegning.keycloak.keybound.examples.backend.store.BackendDataStore
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class ApprovalsController(private val store: BackendDataStore) : ApprovalsApi {
    override fun createApproval(approvalCreateRequest: ApprovalCreateRequest, idempotencyKey: Any?): ResponseEntity<ApprovalCreateResponse> {
        val response = store.createApproval(approvalCreateRequest)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    override fun getApproval(requestId: String): ResponseEntity<ApprovalStatusResponse> {
        val response = store.getApproval(requestId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Approval request not found")
        return ResponseEntity.ok(response)
    }

    override fun cancelApproval(requestId: String): ResponseEntity<Void> {
        val removed = store.cancelApproval(requestId)
        if (!removed) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Approval request not found")
        return ResponseEntity.noContent().build()
    }
}
