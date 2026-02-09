package com.ssegning.keycloak.keybound.enrollment

import com.ssegning.keycloak.keybound.authentcator.AbstractAuthenticator
import org.keycloak.authentication.AuthenticationFlowContext
import org.slf4j.LoggerFactory

class IngestSignedDeviceBlobAuthenticator : AbstractAuthenticator() {
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

        val session = context.authenticationSession
        deviceId?.let { session.setAuthNote("device.id", it) }
        publicKey?.let { session.setAuthNote("device.public_key", it) }
        ts?.let { session.setAuthNote("device.ts", it) }
        nonce?.let { session.setAuthNote("device.nonce", it) }
        sig?.let { session.setAuthNote("device.sig", it) }
        action?.let { session.setAuthNote("device.action", it) }
        aud?.let { session.setAuthNote("device.aud", it) }

        context.success()
    }

    override fun action(context: AuthenticationFlowContext) {
        // No action needed for this authenticator
    }
}
