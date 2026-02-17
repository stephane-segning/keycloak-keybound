package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.authenticator.enrollment.authenticator.AbstractKeyAuthenticator
import com.ssegning.keycloak.keybound.core.helper.computeJkt
import com.ssegning.keycloak.keybound.core.helper.parsePublicJwk
import com.ssegning.keycloak.keybound.core.models.DeviceDescriptor
import com.ssegning.keycloak.keybound.core.models.EnrollmentDecision
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.slf4j.LoggerFactory

class PersistDeviceCredentialAuthenticator(
    private val apiGateway: ApiGateway,
) : AbstractKeyAuthenticator() {
    companion object {
        private val log = LoggerFactory.getLogger(PersistDeviceCredentialAuthenticator::class.java)
        const val DEVICE_OS_NOTE_NAME = "device_os"
        const val DEVICE_MODEL_NOTE_NAME = "device_model"
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val user = context.user
        if (user == null) {
            log.error("User not found in context")
            context.failure(AuthenticationFlowError.UNKNOWN_USER)
            return
        }

        val session = context.authenticationSession
        val deviceId = session.getAuthNote(DEVICE_ID_NOTE_NAME)
        val publicKey = session.getAuthNote(DEVICE_PUBLIC_KEY_NOTE_NAME)
        val deviceOs = session.getAuthNote(DEVICE_OS_NOTE_NAME)?.trim()
        val deviceModel = session.getAuthNote(DEVICE_MODEL_NOTE_NAME) ?: "Unknown"
        val backendUserId =
            session.getAuthNote(KeyboundFlowNotes.BACKEND_USER_ID_NOTE_NAME)
                ?: KeyboundUserResolver.resolveBackendUserId(user)

        log.debug("Persisting device credential for keycloak_user={} backend_user={} device={}", user.id, backendUserId, deviceId)

        if (deviceId == null || publicKey == null || deviceOs.isNullOrBlank()) {
            log.error("Missing device.id, device.public_key, or required device_os in authentication session notes")
            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS)
            return
        }

        try {
            val jkt = computeJkt(publicKey)
            val deviceDescriptor =
                DeviceDescriptor(
                    deviceId = deviceId,
                    jkt = jkt,
                    publicJwk = parsePublicJwk(publicKey),
                    platform = deviceOs,
                    model = deviceModel,
                    appVersion = null,
                )

            val precheck =
                apiGateway.enrollmentPrecheck(
                    context = context,
                    userId = backendUserId,
                    userHint = user.username,
                    deviceData = deviceDescriptor,
                )

            if (precheck == null) {
                log.error("Enrollment precheck failed for user {}", backendUserId)
                context.failure(AuthenticationFlowError.INTERNAL_ERROR)
                return
            }

            precheck.boundUserId?.takeIf { it.isNotBlank() }?.let {
                session.setAuthNote(KeyboundFlowNotes.BACKEND_USER_ID_NOTE_NAME, it)
            }

            when (precheck.decision) {
                EnrollmentDecision.REJECT -> {
                    log.warn("Enrollment precheck rejected for user {}", backendUserId)
                    context.failure(AuthenticationFlowError.ACCESS_DENIED)
                    return
                }

                EnrollmentDecision.REQUIRE_APPROVAL -> {
                    val path = session.getAuthNote(KeyboundFlowNotes.ENROLLMENT_PATH_NOTE_NAME)
                    if (path != KeyboundFlowNotes.ENROLLMENT_PATH_APPROVAL) {
                        log.warn("Enrollment precheck requires approval for user {}", backendUserId)
                        context.failure(AuthenticationFlowError.ACCESS_DENIED)
                        return
                    }
                }

                EnrollmentDecision.ALLOW -> {}
            }

            val bound =
                apiGateway.enrollmentBind(
                    context = context,
                    userId = session.getAuthNote(KeyboundFlowNotes.BACKEND_USER_ID_NOTE_NAME) ?: backendUserId,
                    userHint = user.username,
                    deviceData = deviceDescriptor,
                    attributes =
                        mapOf(
                            "device_os" to deviceOs,
                            "device_model" to deviceModel,
                        ),
                    proof =
                        mapOf(
                            "ts" to (session.getAuthNote(DEVICE_TS_NOTE_NAME) ?: ""),
                            "nonce" to (session.getAuthNote(DEVICE_NONCE_NOTE_NAME) ?: ""),
                        ),
                )

            if (!bound) {
                context.failure(AuthenticationFlowError.INTERNAL_ERROR)
                return
            }

            log.debug("Device credential {} persisted for backend user {}", deviceId, backendUserId)
            context.success()
        } catch (e: Exception) {
            log.error("Failed to persist device credential in backend", e)
            context.failure(AuthenticationFlowError.INTERNAL_ERROR)
        }
    }

    override fun action(context: AuthenticationFlowContext) {
        // No action needed
    }
}
