package com.ssegning.keycloak.keybound.enrollment

import com.ssegning.keycloak.keybound.authentcator.AbstractAuthenticator
import com.ssegning.keycloak.keybound.helper.getEnv
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.keycloak.crypto.*
import org.keycloak.jose.jwk.JWKParser
import org.keycloak.models.SingleUseObjectProvider
import org.slf4j.LoggerFactory
import kotlin.io.encoding.Base64
import kotlin.math.abs

class VerifySignedBlobAuthenticator : AbstractAuthenticator() {
    companion object {
        private val log = LoggerFactory.getLogger(VerifySignedBlobAuthenticator::class.java)
        private val TTL: Long = "MIAO".getEnv()?.toLong() ?: 30
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val session = context.authenticationSession
        val deviceId = session.getAuthNote("device.id")
        val publicKeyJwk = session.getAuthNote("device.public_key")
        val tsStr = session.getAuthNote("device.ts")
        val nonce = session.getAuthNote("device.nonce")
        val sig = session.getAuthNote("device.sig")

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
            val canonicalString = deviceId + publicKeyJwk + tsStr + nonce
            val data = canonicalString.toByteArray(Charsets.UTF_8)

            // Choose decoder depending on what your client sends:
            // - Base64 URL-safe is common for JOSE-ish payloads
            val signatureBytes = Base64.decode(sig)

            val alg = jwk.algorithm ?: run {
                log.error("Missing 'alg' in JWK; cannot verify signature safely")
                context.failure(AuthenticationFlowError.INVALID_CREDENTIALS)
                return
            }

            val key = KeyWrapper().apply {
                setPublicKey(publicKey)
                algorithm = alg
            }

            val verifier: SignatureVerifierContext = when {
                JavaAlgorithm.isECJavaAlgorithm(alg) ->
                    ECDSASignatureVerifierContext(key)

                JavaAlgorithm.isEddsaJavaAlgorithm(alg) || alg == Algorithm.EdDSA -> ServerEdDSASignatureVerifierContext(
                    key
                )

                else -> AsymmetricSignatureVerifierContext(key) // RSA/RS*/PS*
            }

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
