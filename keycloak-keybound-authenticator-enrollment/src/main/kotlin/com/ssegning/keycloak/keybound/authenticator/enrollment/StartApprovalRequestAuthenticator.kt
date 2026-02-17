package com.ssegning.keycloak.keybound.authenticator.enrollment

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
    private val apiGateway: ApiGateway,
) : AbstractAuthenticator() {
    companion object {
        private val log = LoggerFactory.getLogger(StartApprovalRequestAuthenticator::class.java)
        const val APPROVAL_REQUEST_ID_NOTE = "approval.request_id"
    }

    override fun requiresUser() = true

    override fun configuredFor(
        session: KeycloakSession,
        realm: RealmModel,
        user: UserModel?,
    ): Boolean {
        if (user == null) {
            return false
        }

        val backendUserId = KeyboundUserResolver.resolveBackendUserId(user)
        val deviceCount = apiGateway.listUserDevices(backendUserId, false)?.size
        return deviceCount != null && deviceCount > 0
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val authSession = context.authenticationSession
        val authenticatedUser = authSession.authenticatedUser
        val userId =
            authSession.getAuthNote(KeyboundFlowNotes.BACKEND_USER_ID_NOTE_NAME)
                ?: authenticatedUser?.let { KeyboundUserResolver.resolveBackendUserId(it) }
        val deviceId = authSession.getAuthNote(AbstractKeyAuthenticator.DEVICE_ID_NOTE_NAME)
        val publicKeyStr = authSession.getAuthNote(AbstractKeyAuthenticator.DEVICE_PUBLIC_KEY_NOTE_NAME)
        val deviceOs = authSession.getAuthNote(PersistDeviceCredentialAuthenticator.DEVICE_OS_NOTE_NAME)
        val deviceModel = authSession.getAuthNote(PersistDeviceCredentialAuthenticator.DEVICE_MODEL_NOTE_NAME)

        if (userId.isNullOrBlank() || deviceId.isNullOrBlank() || publicKeyStr.isNullOrBlank()) {
            log.error(
                "Missing device details for approval request (deviceId={}, publicKeyPresent={})",
                deviceId,
                !publicKeyStr.isNullOrBlank(),
            )
            context.failure(AuthenticationFlowError.INTERNAL_ERROR)
            return
        }

        val requestId =
            try {
                val deviceDescriptor =
                    DeviceDescriptor(
                        deviceId = deviceId,
                        jkt = computeJkt(publicKeyStr),
                        publicJwk = parsePublicJwk(publicKeyStr),
                        platform = deviceOs,
                        model = deviceModel,
                        appVersion = null,
                    )
                apiGateway.createApprovalRequest(context, userId, deviceDescriptor)
            } catch (e: Exception) {
                log.error("Failed to create approval request payload for user {}", userId, e)
                null
            }

        if (requestId.isNullOrBlank()) {
            context.failure(AuthenticationFlowError.INTERNAL_ERROR)
            return
        }

        authSession.setAuthNote(APPROVAL_REQUEST_ID_NOTE, requestId)
        log.debug("Created approval request {} for user {}", requestId, userId)
        context.success()
    }

    override fun action(context: AuthenticationFlowContext) {
        // No action needed for this authenticator
    }
}
