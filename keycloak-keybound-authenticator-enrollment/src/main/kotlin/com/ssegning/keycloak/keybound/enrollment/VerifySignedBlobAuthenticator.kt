package com.ssegning.keycloak.keybound.enrollment

import com.ssegning.keycloak.keybound.enrollment.authenticator.AbstractKeyAuthenticator
import com.ssegning.keycloak.keybound.helper.getEnv
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.keycloak.crypto.*
import org.keycloak.jose.jwk.JWKParser
import org.keycloak.models.SingleUseObjectProvider
import org.keycloak.util.JsonSerialization
import org.slf4j.LoggerFactory
import kotlin.io.encoding.Base64
import kotlin.math.abs

class VerifySignedBlobAuthenticator : AbstractKeyAuthenticator() {
    companion object {
        private val log = LoggerFactory.getLogger(VerifySignedBlobAuthenticator::class.java)
        private val TTL: Long = "MIAO".getEnv()?.toLong() ?: 30
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val session = context.authenticationSession
        val deviceId = session.getAuthNote(DEVICE_ID_NOTE_NAME)
        val publicKeyJwk = session.getAuthNote(DEVICE_PUBLIC_KEY_NOTE_NAME)
        val tsStr = session.getAuthNote(DEVICE_TS_NOTE_NAME)
        val nonce = session.getAuthNote(DEVICE_NONCE_NOTE_NAME)
        val sig = session.getAuthNote(DEVICE_SIG_NOTE_NAME)

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
        if (abs(currentTime - ts) > TTL) {
            log.error("Timestamp verification failed. Current: $currentTime, Provided: $ts")
            context.failure(AuthenticationFlowError.EXPIRED_CODE)
            return
        }

        // 2. Nonce Replay Prevention (atomic)
        val suo = context.session.getProvider(SingleUseObjectProvider::class.java)
        val nonceKey = "avoid-replay:$nonce"

        // returns true only the first time within TTL
        val firstSeen = suo.putIfAbsent(nonceKey, TTL)
        if (!firstSeen) {
            log.error("Nonce replay detected: $nonce")
            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS)
            return
        }

        // 3. Signature Verification
        try {
            val jwkParser = JWKParser.create().parse(publicKeyJwk)
            val jwk = jwkParser.jwk
            val publicKey = jwkParser.toPublicKey()

            // Construct canonical string: device_id + public_key + ts + nonce
            // SECURITY: Use a structured format (JSON) to prevent canonicalization attacks.
            // Simple concatenation is ambiguous and allows signature forgery.
            val canonicalData = mapOf(
                "deviceId" to deviceId,
                "publicKey" to publicKeyJwk,
                "ts" to tsStr,
                "nonce" to nonce
            )
            val canonicalString = JsonSerialization.writeValueAsString(canonicalData)
            val data = canonicalString.toByteArray(Charsets.UTF_8)

            // Choose decoder depending on what your client sends:
            // - Base64 URL-safe is common for JOSE-ish payloads
            val signatureBytes = Base64.decode(sig)

            // SECURITY: Enforce ES256 to prevent algorithm confusion attacks.
            // We do not trust the 'alg' header from the user-provided JWK.
            val alg = Algorithm.ES256

            val key = KeyWrapper().apply {
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
        context.success()
    }

    private fun buildVerifierContext(
        publicKey: java.security.PublicKey,
        alg: String,
        curve: String?
    ): SignatureVerifierContext {
        val key = KeyWrapper().apply {
            setPublicKey(publicKey)
            setAlgorithm(alg)
            if (curve != null) setCurve(curve)
        }

        return when {
            JavaAlgorithm.isECJavaAlgorithm(alg) ->
                ECDSASignatureVerifierContext(key)          // :contentReference[oaicite:4]{index=4}

            JavaAlgorithm.isEddsaJavaAlgorithm(alg) || alg == org.keycloak.crypto.Algorithm.EdDSA ->
                ServerEdDSASignatureVerifierContext(key)    // :contentReference[oaicite:5]{index=5}

            else ->
                AsymmetricSignatureVerifierContext(key)     // RSA / RS*, PS* etc. :contentReference[oaicite:6]{index=6}
        }
    }

    override fun action(context: AuthenticationFlowContext) {
        // No action needed
    }
}
