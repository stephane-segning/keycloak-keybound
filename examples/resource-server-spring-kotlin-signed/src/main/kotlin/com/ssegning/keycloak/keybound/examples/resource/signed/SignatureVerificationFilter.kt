package com.ssegning.keycloak.keybound.examples.resource.signed

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import kotlin.math.abs

@Component
class SignatureVerificationFilter(
    private val objectMapper: ObjectMapper,
    @param:Value($$"${signature.max-clock-skew-seconds:60}")
    private val maxClockSkewSeconds: Long
) : OncePerRequestFilter() {
    companion object {
        private const val HEADER_SIGNATURE = "x-signature"
        private const val HEADER_SIGNATURE_TS = "x-signature-timestamp"
        private const val HEADER_PUBLIC_KEY = "x-public-key"
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return request.requestURI == "/health" || request.requestURI == "/actuator/health"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val jwt = resolveJwtPrincipal()
        if (jwt == null) {
            filterChain.doFilter(request, response)
            return
        }

        val jkt = extractJkt(jwt)
        if (jkt.isNullOrBlank()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing cnf.jkt claim")
            return
        }

        val signatureHeader = request.getHeader(HEADER_SIGNATURE)?.trim().orEmpty()
        val signatureTsHeader = request.getHeader(HEADER_SIGNATURE_TS)?.trim().orEmpty()
        val publicKeyHeader = request.getHeader(HEADER_PUBLIC_KEY)?.trim().orEmpty()

        if (signatureHeader.isBlank() || signatureTsHeader.isBlank() || publicKeyHeader.isBlank()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing signature headers")
            return
        }

        val signatureTs = signatureTsHeader.toLongOrNull()
        if (signatureTs == null) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid x-signature-timestamp")
            return
        }

        val now = System.currentTimeMillis() / 1000
        if (abs(now - signatureTs) > maxClockSkewSeconds) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Signature timestamp out of allowed skew")
            return
        }

        val publicJwk = parsePublicJwk(publicKeyHeader)
        if (publicJwk == null) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid x-public-key JWK")
            return
        }

        val computedThumbprint = computeEcJwkThumbprint(publicJwk)
        if (computedThumbprint == null || computedThumbprint != jkt) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "x-public-key does not match cnf.jkt")
            return
        }

        val publicKey = createEcPublicKey(publicJwk)
        if (publicKey == null) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Unable to parse EC public key")
            return
        }

        val signatureBytes = decodeBase64Url(signatureHeader)
        if (signatureBytes == null) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid x-signature encoding")
            return
        }

        val canonicalPayload = buildCanonicalPayload(request, signatureTsHeader)
        if (!verifySignature(publicKey, canonicalPayload, signatureBytes)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid signature")
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun resolveJwtPrincipal(): Jwt? =
        SecurityContextHolder.getContext().authentication?.principal as? Jwt

    private fun extractJkt(jwt: Jwt): String? {
        val cnf = jwt.claims["cnf"] as? Map<*, *> ?: return null
        return cnf["jkt"] as? String
    }

    private fun parsePublicJwk(raw: String): Map<String, String>? {
        return try {
            val parsed = objectMapper.readValue(raw, Map::class.java)
            parsed.entries.associate { (key, value) -> key.toString() to value.toString() }
        } catch (_: Exception) {
            null
        }
    }

    private fun computeEcJwkThumbprint(jwk: Map<String, String>): String? {
        val kty = jwk["kty"]
        val crv = jwk["crv"]
        val x = jwk["x"]
        val y = jwk["y"]
        if (kty != "EC" || crv != "P-256" || x.isNullOrBlank() || y.isNullOrBlank()) {
            return null
        }

        val canonical = "{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\"$x\",\"y\":\"$y\"}"
        val hash = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    private fun createEcPublicKey(jwk: Map<String, String>): ECPublicKey? {
        val x = decodeBase64Url(jwk["x"] ?: return null) ?: return null
        val y = decodeBase64Url(jwk["y"] ?: return null) ?: return null

        val algorithmParameters = java.security.AlgorithmParameters.getInstance("EC")
        val ecSpec: AlgorithmParameterSpec = ECGenParameterSpec("secp256r1")
        algorithmParameters.init(ecSpec)
        val parameterSpec = algorithmParameters.getParameterSpec(ECParameterSpec::class.java)

        val point = ECPoint(BigInteger(1, x), BigInteger(1, y))
        val keySpec = ECPublicKeySpec(point, parameterSpec)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(keySpec) as? ECPublicKey
    }

    private fun buildCanonicalPayload(request: HttpServletRequest, signatureTs: String): String {
        val method = request.method
        val path = request.requestURI
        val query = request.queryString ?: ""
        return listOf(method, path, query, signatureTs).joinToString("\n")
    }

    private fun verifySignature(publicKey: ECPublicKey, canonicalPayload: String, signatureBytes: ByteArray): Boolean {
        return try {
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(publicKey)
            verifier.update(canonicalPayload.toByteArray(StandardCharsets.UTF_8))
            verifier.verify(signatureBytes)
        } catch (_: Exception) {
            false
        }
    }

    private fun decodeBase64Url(value: String): ByteArray? {
        return try {
            Base64.getUrlDecoder().decode(value)
        } catch (_: Exception) {
            null
        }
    }

    private fun reject(response: HttpServletResponse, status: Int, message: String) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = StandardCharsets.UTF_8.name()
        val payload = mapOf(
            "error" to "signature_verification_failed",
            "message" to message
        )
        response.writer.write(objectMapper.writeValueAsString(payload))
    }
}
