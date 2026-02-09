package com.ssegning.keycloak.keybound.enrollment

import com.ssegning.keycloak.keybound.enrollment.authenticator.AbstractKeyAuthenticator
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.slf4j.LoggerFactory

class IngestSignedDeviceBlobAuthenticator : AbstractKeyAuthenticator() {
    companion object {
        private val log = LoggerFactory.getLogger(IngestSignedDeviceBlobAuthenticator::class.java)
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val httpRequest = context.httpRequest
        val queryParameters = httpRequest.decodedFormParameters

        val deviceId = queryParameters.getFirst("device_id")
        val publicKey = queryParameters.getFirst("public_key")
        val ts = queryParameters.getFirst("ts")
        val nonce = queryParameters.getFirst("nonce")
        val sig = queryParameters.getFirst("sig")
        val action = queryParameters.getFirst("action")
        val aud = queryParameters.getFirst("aud")

        // Input length validation to prevent potential DoS attacks
        // We limit the length of each parameter to 2048 characters to avoid unbounded input storage
        val maxInputLength = 2048
        if (listOf(deviceId, publicKey, ts, nonce, sig, action, aud).any { it != null && it.length > maxInputLength }) {
            log.warn("Input parameter exceeded maximum length of $maxInputLength characters")
            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS)
            return
        }

        val session = context.authenticationSession
        deviceId?.let { session.setAuthNote(DEVICE_ID_NOTE_NAME, it) }
        publicKey?.let { session.setAuthNote(DEVICE_PUBLIC_KEY_NOTE_NAME, it) }
        ts?.let { session.setAuthNote(DEVICE_TS_NOTE_NAME, it) }
        nonce?.let { session.setAuthNote(DEVICE_NONCE_NOTE_NAME, it) }
        sig?.let { session.setAuthNote(DEVICE_SIG_NOTE_NAME, it) }
        action?.let { session.setAuthNote(DEVICE_ACTION_NOTE_NAME, it) }
        aud?.let { session.setAuthNote(DEVICE_AUD_NOTE_NAME, it) }

        context.success()
    }

    override fun action(context: AuthenticationFlowContext) {
        // No action needed for this authenticator
    }
}
