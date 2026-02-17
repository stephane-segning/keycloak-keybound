package com.ssegning.keycloak.keybound.core.helper

import org.keycloak.util.JsonSerialization
import java.security.MessageDigest
import java.util.Base64
import java.util.TreeMap

private val REQUIRED_THUMBPRINT_MEMBERS =
    mapOf(
        "EC" to listOf("crv", "kty", "x", "y"),
        "RSA" to listOf("e", "kty", "n"),
        "OKP" to listOf("crv", "kty", "x"),
    )

fun parsePublicJwk(rawJwk: String): Map<String, Any> {
    @Suppress("UNCHECKED_CAST")
    return JsonSerialization.readValue(rawJwk, MutableMap::class.java) as Map<String, Any>
}

fun computeJkt(rawJwk: String): String {
    val jwk = parsePublicJwk(rawJwk)
    val kty =
        jwk["kty"]?.toString()
            ?: throw IllegalArgumentException("JWK missing 'kty'")
    val requiredMembers =
        REQUIRED_THUMBPRINT_MEMBERS[kty]
            ?: throw IllegalArgumentException("Unsupported JWK key type '$kty'")

    val canonicalMap = TreeMap<String, String>()
    requiredMembers.forEach { member ->
        val value =
            jwk[member]?.toString()
                ?: throw IllegalArgumentException("JWK missing '$member'")
        canonicalMap[member] = value
    }

    val canonicalJson = JsonSerialization.writeValueAsString(canonicalMap)
    val hash =
        MessageDigest
            .getInstance("SHA-256")
            .digest(canonicalJson.toByteArray(Charsets.UTF_8))

    return Base64
        .getUrlEncoder()
        .withoutPadding()
        .encodeToString(hash)
}
