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
    private val apiGateway: ApiGateway
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
        val deviceOs = session.getAuthNote(DEVICE_OS_NOTE_NAME) ?: "Unknown"
        val deviceModel = session.getAuthNote(DEVICE_MODEL_NOTE_NAME) ?: "Unknown"

        if (deviceId == null || publicKey == null) {
            log.error("Missing device.id or device.public_key in authentication session notes")
            context.failure(AuthenticationFlowError.INTERNAL_ERROR)
            return
        }

        try {
            val jkt = computeJkt(publicKey)
            val deviceDescriptor = DeviceDescriptor(
                deviceId = deviceId,
                jkt = jkt,
                publicJwk = parsePublicJwk(publicKey),
                platform = deviceOs,
                model = deviceModel,
                appVersion = null
            )

            val precheck = apiGateway.enrollmentPrecheck(
                context = context,
                userId = user.id,
                userHint = user.username,
                deviceData = deviceDescriptor
            ) ?: run {
                context.failure(AuthenticationFlowError.INTERNAL_ERROR)
                return
            }

            if (precheck.decision != EnrollmentDecision.ALLOW) {
                log.warn(
                    "Enrollment precheck denied for user={}, device={}, decision={}, reason={}",
                    user.id,
                    deviceId,
                    precheck.decision,
                    precheck.reason
                )
                context.failure(AuthenticationFlowError.ACCESS_DENIED)
                return
            }

            val bound = apiGateway.enrollmentBind(
                context = context,
                userId = user.id,
                userHint = user.username,
                deviceData = deviceDescriptor,
                attributes = mapOf(
                    "device_os" to deviceOs,
                    "device_model" to deviceModel
                ),
                proof = mapOf(
                    "ts" to (session.getAuthNote(DEVICE_TS_NOTE_NAME) ?: ""),
                    "nonce" to (session.getAuthNote(DEVICE_NONCE_NOTE_NAME) ?: "")
                )
            )

            if (!bound) {
                context.failure(AuthenticationFlowError.INTERNAL_ERROR)
                return
            }

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
