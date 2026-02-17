package com.ssegning.keycloak.keybound.core.models

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import org.keycloak.util.JsonSerialization

@JsonPropertyOrder(value = ["nonce", "deviceId", "username", "ts", "publicKey"])
data class PublicKeyLoginSignaturePayload(
    val nonce: String,
    val deviceId: String,
    val username: String,
    val ts: String,
    val publicKey: String,
) {
    fun toCanonicalJson(): String = JsonSerialization.writeValueAsString(this)
}
