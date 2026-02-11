package com.ssegning.keycloak.keybound.examples.backend.controller

import com.ssegning.keycloak.keybound.examples.backend.api.EnrollmentApi
import com.ssegning.keycloak.keybound.examples.backend.model.*
import com.ssegning.keycloak.keybound.examples.backend.store.BackendDataStore
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class EnrollmentController(private val store: BackendDataStore) : EnrollmentApi {
    override fun enrollmentPrecheck(
        enrollmentPrecheckRequest: EnrollmentPrecheckRequest,
        idempotencyKey: Any?
    ): ResponseEntity<EnrollmentPrecheckResponse> {
        log.info(
            "Enrollment precheck for device {} and hint {}",
            enrollmentPrecheckRequest.deviceId,
            enrollmentPrecheckRequest.userHint
        )
        val response = store.precheck(enrollmentPrecheckRequest)
        log.debug("Precheck result {} for device {}", response.decision, enrollmentPrecheckRequest.deviceId)
        return ResponseEntity.ok(response)
    }

    override fun enrollmentBind(
        enrollmentBindRequest: EnrollmentBindRequest,
        idempotencyKey: Any?
    ): ResponseEntity<EnrollmentBindResponse> {
        log.info("Binding device {} to user {}", enrollmentBindRequest.deviceId, enrollmentBindRequest.userId)
        val response = store.bindDevice(enrollmentBindRequest)
        log.debug("Enrollment bind response status {}", response.status)
        return ResponseEntity.ok(response)
    }

    override fun confirmSms(smsConfirmRequest: SmsConfirmRequest): ResponseEntity<SmsConfirmResponse> {
        log.info("Confirming SMS hash {}", smsConfirmRequest.hash)
        val response = store.confirmSms(smsConfirmRequest)
        log.debug("SMS confirm result {}", response)
        return ResponseEntity.ok(response)
    }

    override fun sendSms(smsSendRequest: SmsSendRequest): ResponseEntity<SmsSendResponse> {
        log.info("Sending SMS to phone {}", smsSendRequest.phoneNumber)
        val response = store.sendSms(smsSendRequest)
        log.debug("SMS send response {}", response)
        return ResponseEntity.ok(response)
    }

    override fun resolveUserByPhone(phoneResolveRequest: PhoneResolveRequest): ResponseEntity<PhoneResolveResponse> {
        log.info("Resolving user by phone {}", phoneResolveRequest.phoneNumber)
        val response = store.resolveUserByPhone(phoneResolveRequest)
        log.debug(
            "Resolved phone {} -> exists={} path={}",
            phoneResolveRequest.phoneNumber,
            response.userExists,
            response.enrollmentPath
        )
        return ResponseEntity.ok(response)
    }

    override fun resolveOrCreateUserByPhone(
        phoneResolveOrCreateRequest: PhoneResolveOrCreateRequest
    ): ResponseEntity<PhoneResolveOrCreateResponse> {
        log.info("Resolve or create user by phone {}", phoneResolveOrCreateRequest.phoneNumber)
        val response = store.resolveOrCreateUserByPhone(phoneResolveOrCreateRequest)
        log.debug(
            "Resolve/create phone {} -> userId={} created={}",
            phoneResolveOrCreateRequest.phoneNumber,
            response.userId,
            response.created
        )
        return ResponseEntity.ok(response)
    }

    companion object {
        private val log = LoggerFactory.getLogger(EnrollmentController::class.java)
    }
}
