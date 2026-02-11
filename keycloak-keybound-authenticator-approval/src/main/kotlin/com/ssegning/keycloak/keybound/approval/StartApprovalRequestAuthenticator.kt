package com.ssegning.keycloak.keybound.approval

import com.ssegning.keycloak.keybound.authenticator.enrollment.PersistDeviceCredentialAuthenticator
import com.ssegning.keycloak.keybound.authenticator.enrollment.authenticator.AbstractKeyAuthenticator
import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticator
import com.ssegning.keycloak.keybound.core.helper.computeJkt
import com.ssegning.keycloak.keybound.core.helper.parsePublicJwk
import com.ssegning.keycloak.keybound.core.models.DeviceDescriptor
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel
import org.slf4j.LoggerFactory

class StartApprovalRequestAuthenticator(
    private val apiGateway: ApiGateway
) : AbstractAuthenticator() {
    companion object {
        private val log = LoggerFactory.getLogger(StartApprovalRequestAuthenticator::class.java)
        const val APPROVAL_REQUEST_ID_NOTE = "approval.request_id"
    }

    override fun requiresUser() = true

    override fun configuredFor(session: KeycloakSession, realm: RealmModel, user: UserModel?): Boolean {
        if (user == null) {
            return false
        }

        val deviceCount = apiGateway.listUserDevices(user.id, false)?.size
        return deviceCount != null && deviceCount > 0
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val session = context.authenticationSession
        val userId = session.authenticatedUser.id

        val deviceId = session.getAuthNote(AbstractKeyAuthenticator.DEVICE_ID_NOTE_NAME)
        val publicKeyStr = session.getAuthNote(AbstractKeyAuthenticator.DEVICE_PUBLIC_KEY_NOTE_NAME)
        val deviceOs = session.getAuthNote(PersistDeviceCredentialAuthenticator.DEVICE_OS_NOTE_NAME)
        val deviceModel = session.getAuthNote(PersistDeviceCredentialAuthenticator.DEVICE_MODEL_NOTE_NAME)

        log.debug("Starting approval request for user={} device={}", userId, deviceId)
        if (deviceId.isNullOrBlank() || publicKeyStr.isNullOrBlank()) {
            log.error(
                "Missing device details (deviceId={} publicKey={}) in session",
                deviceId,
                publicKeyStr?.let { "[REDACTED]" })
            context.failure(AuthenticationFlowError.INTERNAL_ERROR)
            return
        }

        val requestId = try {
            val deviceDescriptor = DeviceDescriptor(
                deviceId = deviceId,
                jkt = computeJkt(publicKeyStr),
                publicJwk = parsePublicJwk(publicKeyStr),
                platform = deviceOs,
                model = deviceModel,
                appVersion = null
            )
            apiGateway.createApprovalRequest(context, userId, deviceDescriptor)
        } catch (e: Exception) {
            log.error("Failed to build approval request payload", e)
            null
        }

        if (requestId != null) {
            session.setAuthNote(APPROVAL_REQUEST_ID_NOTE, requestId)
            log.debug("Recorded approval request {} for user {}", requestId, userId)
            context.success()
        } else {
            context.failure(AuthenticationFlowError.INTERNAL_ERROR)
        }
    }

    override fun action(context: AuthenticationFlowContext) {
        // No action needed
    }
}
