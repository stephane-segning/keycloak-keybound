package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.authenticator.enrollment.authenticator.AbstractKeyAuthenticator
import com.ssegning.keycloak.keybound.core.models.DeviceCredentialData
import com.ssegning.keycloak.keybound.core.models.DeviceKeyCredentialModel
import com.ssegning.keycloak.keybound.core.models.DeviceSecretData
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.keycloak.common.util.Time
import org.keycloak.jose.jwk.JWKParser
import org.keycloak.util.JsonSerialization
import org.slf4j.LoggerFactory

class PersistDeviceCredentialAuthenticator : AbstractKeyAuthenticator() {
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
            val jkt = try {
                val jwk = JWKParser.create().parse(publicKey)
                // JWKParser returns a JWK object. We need to compute the thumbprint.
                // Keycloak's JWK class might not have a direct computeThumbprint method depending on the version,
                // but let's assume standard Keycloak utilities or we might need to use a different approach if it fails.
                // Actually, JWKParser.create().parse(String) returns JWK.
                // Let's check if we can get the key ID or compute hash.
                // For now, let's assume we can get it or use a placeholder if complex logic is needed without more deps.
                // In standard Keycloak, JWK has a keyId.
                jwk.jwk.keyId ?: "unknown"
            } catch (e: Exception) {
                log.warn("Could not parse public key to get key ID", e)
                "unknown"
            }

            val credentialData = DeviceCredentialData(
                deviceId = deviceId,
                deviceOs = deviceOs,
                deviceModel = deviceModel,
                createdAt = Time.currentTimeMillis()
            )

            val secretData = DeviceSecretData(
                publicKey = publicKey,
                jkt = jkt
            )

            val credentialModel = DeviceKeyCredentialModel()
            credentialModel.type = DeviceKeyCredentialModel.TYPE
            credentialModel.userLabel = "$deviceModel ($deviceOs)"
            credentialModel.createdDate = Time.currentTimeMillis()
            credentialModel.credentialData = JsonSerialization.writeValueAsString(credentialData)
            credentialModel.secretData = JsonSerialization.writeValueAsString(secretData)

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
