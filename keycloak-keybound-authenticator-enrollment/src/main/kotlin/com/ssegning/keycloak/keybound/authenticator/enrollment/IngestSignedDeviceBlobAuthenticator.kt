package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.authenticator.enrollment.authenticator.AbstractKeyAuthenticator
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.slf4j.LoggerFactory

class IngestSignedDeviceBlobAuthenticator : AbstractKeyAuthenticator() {
    companion object {
        private val log = LoggerFactory.getLogger(IngestSignedDeviceBlobAuthenticator::class.java)
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val httpRequest = context.httpRequest
        val formParameters = httpRequest.decodedFormParameters
        val queryParameters = context.uriInfo.queryParameters

        fun param(name: String): String? = queryParameters.getFirst(name) ?: formParameters.getFirst(name)

        val deviceId = param("device_id")
        val publicKey = param("public_key")
        val ts = param("ts")
        val nonce = param("nonce")
        val sig = param("sig")
        val action = param("action")
        val aud = param("aud")
        val userHint = param("user_hint") ?: param("username")
        val deviceOs = param("device_os")
        val deviceModel = param("device_model")

        log.debug(
            "Ingesting signed blob deviceId={} action={} userHint={} deviceOs={} deviceModel={}",
            deviceId,
            action,
            userHint,
            deviceOs,
            deviceModel,
        )

        // Input length validation to prevent potential DoS attacks
        // We limit the length of each parameter to 2048 characters to avoid unbounded input storage
        val maxInputLength = 2048
        if (listOf(deviceId, publicKey, ts, nonce, sig, action, aud, userHint, deviceOs, deviceModel)
                .any { it != null && it.length > maxInputLength }
        ) {
            log.warn("Input parameter exceeded maximum length of $maxInputLength characters")
            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS)
            return
        }

        val normalizedDeviceOs = deviceOs?.trim()
        if (normalizedDeviceOs.isNullOrBlank()) {
            log.warn("Missing required device_os parameter")
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
        userHint?.let { session.setAuthNote(USER_HINT_NOTE_NAME, it) }
        session.setAuthNote(PersistDeviceCredentialAuthenticator.DEVICE_OS_NOTE_NAME, normalizedDeviceOs)
        deviceModel?.trim()?.takeIf { it.isNotBlank() }?.let {
            session.setAuthNote(PersistDeviceCredentialAuthenticator.DEVICE_MODEL_NOTE_NAME, it)
        }

        context.success()
    }

    override fun action(context: AuthenticationFlowContext) {
        // No action needed for this authenticator
    }
}
