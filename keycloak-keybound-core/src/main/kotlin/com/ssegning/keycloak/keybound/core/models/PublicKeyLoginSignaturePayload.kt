package com.ssegning.keycloak.keybound.core.models

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import org.keycloak.util.JsonSerialization

@JsonPropertyOrder(value = ["nonce", "deviceId", "ts", "publicKey"])
data class PublicKeyLoginSignaturePayload(
    val nonce: String,
    val deviceId: String,
    val ts: String,
    val publicKey: String,
) {
    fun toCanonicalJson(): String = JsonSerialization.writeValueAsString(this)
}
