package com.ssegning.keycloak.keybound.approval

import com.ssegning.keycloak.keybound.authenticator.enrollment.PersistDeviceCredentialAuthenticator
import com.ssegning.keycloak.keybound.authenticator.enrollment.authenticator.AbstractKeyAuthenticator
import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticator
import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticatorFactory
import com.ssegning.keycloak.keybound.core.helper.computeJkt
import com.ssegning.keycloak.keybound.core.helper.parsePublicJwk
import com.ssegning.keycloak.keybound.core.models.DeviceDescriptor
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.keycloak.models.KeycloakSession
import org.slf4j.LoggerFactory

class StartApprovalRequestAuthenticator(
    private val apiGateway: ApiGateway
) : AbstractAuthenticator() {
    companion object {
        private val log = LoggerFactory.getLogger(StartApprovalRequestAuthenticator::class.java)
        const val APPROVAL_REQUEST_ID_NOTE = "approval.request_id"
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val session = context.authenticationSession
        val userId = session.authenticatedUser.id

        val deviceId = session.getAuthNote(AbstractKeyAuthenticator.DEVICE_ID_NOTE_NAME)
        val publicKeyStr = session.getAuthNote(AbstractKeyAuthenticator.DEVICE_PUBLIC_KEY_NOTE_NAME)
        val deviceOs = session.getAuthNote(PersistDeviceCredentialAuthenticator.DEVICE_OS_NOTE_NAME)
        val deviceModel = session.getAuthNote(PersistDeviceCredentialAuthenticator.DEVICE_MODEL_NOTE_NAME)

        if (deviceId == null || publicKeyStr == null) {
            log.error("Missing device details in session")
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
            context.success()
        } else {
            context.failure(AuthenticationFlowError.INTERNAL_ERROR)
        }
    }

    override fun action(context: AuthenticationFlowContext) {
        // No action needed
    }
}

class StartApprovalRequestAuthenticatorFactory : AbstractAuthenticatorFactory() {
    override fun getId(): String = "keybound-start-approval-request"

    override fun getDisplayType(): String = "Keybound: Start Approval Request"

    override fun getHelpText(): String = "Initiates a device approval request with the backend."

    override fun create(session: KeycloakSession, apiGateway: ApiGateway): StartApprovalRequestAuthenticator =
        StartApprovalRequestAuthenticator(apiGateway)
}
