package com.ssegning.keycloak.keybound.examples.backend.controller

import com.ssegning.keycloak.keybound.examples.backend.api.EnrollmentApi
import com.ssegning.keycloak.keybound.examples.backend.model.*
import com.ssegning.keycloak.keybound.examples.backend.store.BackendDataStore
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class EnrollmentController(private val store: BackendDataStore) : EnrollmentApi {
    override fun enrollmentBind(
        enrollmentBindRequest: EnrollmentBindRequest,
        idempotencyKey: Any?
    ): ResponseEntity<EnrollmentBindResponse> {
        log.info("Binding device {} to user {}", enrollmentBindRequest.deviceId, enrollmentBindRequest.userId)
        val response = store.bindDevice(enrollmentBindRequest)
        log.debug("Enrollment bind response status {}", response.status)
        return ResponseEntity.ok(response)
    }

    companion object {
        private val log = LoggerFactory.getLogger(EnrollmentController::class.java)
    }
}
