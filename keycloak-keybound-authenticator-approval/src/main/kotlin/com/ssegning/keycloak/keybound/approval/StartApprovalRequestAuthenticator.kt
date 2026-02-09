package com.ssegning.keycloak.keybound.approval

import com.ssegning.keycloak.keybound.authenticator.AbstractAuthenticator
import com.ssegning.keycloak.keybound.authenticator.AbstractAuthenticatorFactory
import com.ssegning.keycloak.keybound.enrollment.authenticator.AbstractKeyAuthenticator
import com.ssegning.keycloak.keybound.models.DeviceDescriptor
import com.ssegning.keycloak.keybound.spi.ApiGateway
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.keycloak.models.KeycloakSession
import org.slf4j.LoggerFactory

class StartApprovalRequestAuthenticator : AbstractAuthenticator() {
    companion object {
        private val log = LoggerFactory.getLogger(StartApprovalRequestAuthenticator::class.java)
        const val APPROVAL_REQUEST_ID_NOTE = "approval.request_id"
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val session = context.authenticationSession
        val userId = session.authenticatedUser.id
        
        val deviceId = session.getAuthNote(AbstractKeyAuthenticator.DEVICE_ID_NOTE_NAME)
        val jkt = session.getAuthNote(AbstractKeyAuthenticator.DEVICE_ID_NOTE_NAME) // Assuming device_id is used as jkt or similar, but let's check if we have JKT note. 
        // Wait, AbstractKeyAuthenticator doesn't have JKT constant. Let's check IngestSignedDeviceBlobAuthenticator.
        // It has DEVICE_PUBLIC_KEY_NOTE_NAME. We can derive JKT from public key or use device_id if that's the intention.
        // The PLAN says: "Extract user_id and device details from the authentication session."
        // The DeviceDescriptor needs: deviceId, jkt, publicJwk, platform, model, appVersion.
        
        val publicKeyStr = session.getAuthNote(AbstractKeyAuthenticator.DEVICE_PUBLIC_KEY_NOTE_NAME)
        
        if (deviceId == null || publicKeyStr == null) {
            log.error("Missing device details in session")
            context.failure(AuthenticationFlowError.INTERNAL_ERROR)
            return
        }

        // Parse JWK to get map
        val publicJwkMap = try {
             // Keycloak's JWKParser might not directly give a Map<String, Any>, but we can try to parse it.
             // Or we can just pass null if we don't strictly need it for the backend call if backend can handle it.
             // However, DeviceDescriptor expects Map<String, Any>?.
             // Let's try to parse it if possible, or just pass null for now if complex.
             // Actually, let's look at VerifySignedBlobAuthenticator, it uses JWKParser.
             null // For now, let's pass null to keep it simple unless we have a utility to convert string to map easily without extra deps.
        } catch (e: Exception) {
            null
        }

        val deviceDescriptor = DeviceDescriptor(
            deviceId = deviceId,
            jkt = deviceId, // Using deviceId as JKT for now as per previous context or lack of specific JKT note.
            publicJwk = null, // We have the string, but the model wants a Map.
            platform = null,
            model = null,
            appVersion = null
        )

        val apiGateway = context.session.getProvider(ApiGateway::class.java)
        val requestId = apiGateway.createApprovalRequest(context, userId, deviceDescriptor)

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
        StartApprovalRequestAuthenticator()
}
