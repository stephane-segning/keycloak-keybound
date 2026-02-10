package com.ssegning.keycloak.keybound.examples.backend.controller

import com.ssegning.keycloak.keybound.examples.backend.api.EnrollmentApi
import com.ssegning.keycloak.keybound.examples.backend.model.*
import com.ssegning.keycloak.keybound.examples.backend.store.BackendDataStore
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class EnrollmentController(private val store: BackendDataStore) : EnrollmentApi {
    override fun enrollmentPrecheck(
        enrollmentPrecheckRequest: EnrollmentPrecheckRequest,
        idempotencyKey: Any?
    ): ResponseEntity<EnrollmentPrecheckResponse> {
        val response = store.precheck(enrollmentPrecheckRequest)
        return ResponseEntity.ok(response)
    }

    override fun enrollmentBind(
        enrollmentBindRequest: EnrollmentBindRequest,
        idempotencyKey: Any?
    ): ResponseEntity<EnrollmentBindResponse> {
        val response = store.bindDevice(enrollmentBindRequest)
        return ResponseEntity.ok(response)
    }

    override fun confirmSms(smsConfirmRequest: SmsConfirmRequest): ResponseEntity<SmsConfirmResponse> {
        val response = store.confirmSms(smsConfirmRequest)
        return ResponseEntity.ok(response)
    }

    override fun sendSms(smsSendRequest: SmsSendRequest): ResponseEntity<SmsSendResponse> {
        val response = store.sendSms(smsSendRequest)
        return ResponseEntity.ok(response)
    }
}
