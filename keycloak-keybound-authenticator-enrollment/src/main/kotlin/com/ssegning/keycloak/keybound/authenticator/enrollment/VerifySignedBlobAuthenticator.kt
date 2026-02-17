package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.authenticator.enrollment.authenticator.AbstractKeyAuthenticator
import com.ssegning.keycloak.keybound.core.models.DeviceSignaturePayload
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.keycloak.crypto.Algorithm
import org.keycloak.crypto.ECDSASignatureVerifierContext
import org.keycloak.crypto.KeyWrapper
import org.keycloak.jose.jwk.JWKParser
import org.keycloak.models.SingleUseObjectProvider
import org.slf4j.LoggerFactory
import java.util.Base64
import kotlin.math.abs

class VerifySignedBlobAuthenticator(
    val ttl: Long,
) : AbstractKeyAuthenticator() {
    companion object {
        private val log = LoggerFactory.getLogger(VerifySignedBlobAuthenticator::class.java)
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val session = context.authenticationSession
        val deviceId = session.getAuthNote(DEVICE_ID_NOTE_NAME)
        val publicKeyJwk = session.getAuthNote(DEVICE_PUBLIC_KEY_NOTE_NAME)
        val tsStr = session.getAuthNote(DEVICE_TS_NOTE_NAME)
        val nonce = session.getAuthNote(DEVICE_NONCE_NOTE_NAME)
        val sig = session.getAuthNote(DEVICE_SIG_NOTE_NAME)

        log.debug("Verifying signed blob deviceId={}", deviceId)
        if (deviceId == null || publicKeyJwk == null || tsStr == null || nonce == null || sig == null) {
            log.error("Missing required authentication notes for signature verification")
            context.failure(AuthenticationFlowError.INTERNAL_ERROR)
            return
        }

        // 1. Timestamp Verification
        val ts = tsStr.toLongOrNull()
        if (ts == null) {
            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS)
            return
        }
        val currentTime = System.currentTimeMillis() / 1000
        if (abs(currentTime - ts) > ttl) {
            log.error("Timestamp verification failed. Current: $currentTime, Provided: $ts")
            context.failure(AuthenticationFlowError.EXPIRED_CODE)
            return
        }

        // 2. Nonce Replay Prevention (atomic)
        val suo = context.session.getProvider(SingleUseObjectProvider::class.java)
        val nonceKey = "avoid-replay:${context.realm.name}:$nonce"

        // returns true only the first time within TTL
        val firstSeen = suo.putIfAbsent(nonceKey, ttl)
        if (!firstSeen) {
            log.error("Nonce replay detected: $nonce")
            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS)
            return
        }

        // 3. Signature Verification
        try {
            val jwkParser = JWKParser.create().parse(publicKeyJwk)
            val publicKey = jwkParser.toPublicKey()

            val canonicalString =
                DeviceSignaturePayload(
                    deviceId = deviceId,
                    publicKey = publicKeyJwk,
                    ts = tsStr,
                    nonce = nonce,
                ).toCanonicalJson()
            val data = canonicalString.toByteArray(Charsets.UTF_8)

            val signatureBytes = decodeBase64OrBase64Url(sig)
            val alg = Algorithm.ES256

            val key =
                KeyWrapper().apply {
                    setPublicKey(publicKey)
                    algorithm = alg
                }

            val verifier = ECDSASignatureVerifierContext(key)

            if (!verifier.verify(data, signatureBytes)) {
                log.error("Signature verification failed for device: $deviceId")
                context.failure(AuthenticationFlowError.INVALID_CREDENTIALS)
                return
            }
        } catch (e: Exception) {
            log.error("Error verifying signature", e)
            context.failure(AuthenticationFlowError.INTERNAL_ERROR)
            return
        }

        session.setAuthNote("device_proof", "ok")
        log.debug("Signature verification succeeded for device {}", deviceId)
        context.success()
    }

    override fun action(context: AuthenticationFlowContext) {
        // No action needed
    }

    private fun decodeBase64OrBase64Url(value: String): ByteArray =
        try {
            Base64.getUrlDecoder().decode(value)
        } catch (_: IllegalArgumentException) {
            Base64.getDecoder().decode(value)
        }
}
