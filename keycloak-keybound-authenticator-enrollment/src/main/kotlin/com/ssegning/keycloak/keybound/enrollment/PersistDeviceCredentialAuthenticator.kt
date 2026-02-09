package com.ssegning.keycloak.keybound.enrollment

import com.ssegning.keycloak.keybound.authentcator.AbstractAuthenticator
import com.ssegning.keycloak.keybound.models.DeviceKeyCredentialModel
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.slf4j.LoggerFactory

class PersistDeviceCredentialAuthenticator : AbstractAuthenticator() {
    companion object {
        private val log = LoggerFactory.getLogger(PersistDeviceCredentialAuthenticator::class.java)
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val user = context.user
        if (user == null) {
            log.error("User not found in context")
            context.failure(AuthenticationFlowError.UNKNOWN_USER)
            return
        }

        val session = context.authenticationSession
        val deviceId = session.getAuthNote("device.id")
        val publicKey = session.getAuthNote("device.public_key")

        if (deviceId == null || publicKey == null) {
            log.error("Missing device.id or device.public_key in authentication session notes")
            context.failure(AuthenticationFlowError.INTERNAL_ERROR)
            return
        }

        try {
            val credentialModel = DeviceKeyCredentialModel()
            credentialModel.type = DeviceKeyCredentialModel.TYPE
            credentialModel.secretData = publicKey
            credentialModel.credentialData = deviceId

            context.user
                .credentialManager()
                .createStoredCredential(credentialModel)

            context.success()
        } catch (e: Exception) {
            log.error("Failed to persist device credential", e)
            context.failure(AuthenticationFlowError.INTERNAL_ERROR)
        }
    }

    override fun action(context: AuthenticationFlowContext) {
        // No action needed
    }
}
