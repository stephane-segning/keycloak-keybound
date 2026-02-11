package com.ssegning.keycloak.keybound.examples.resource.signed

data class RequestSignaturePayload(
    val method: String,
    val path: String,
    val query: String,
    val timestamp: String
) {
    fun toCanonicalString(): String =
        listOf(method.uppercase(), path, query, timestamp).joinToString("\n")
}
