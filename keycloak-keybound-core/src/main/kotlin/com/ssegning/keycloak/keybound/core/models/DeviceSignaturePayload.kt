package com.ssegning.keycloak.keybound.core.models

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import org.keycloak.util.JsonSerialization

@JsonPropertyOrder(value = ["deviceId", "publicKey", "ts", "nonce"])
data class DeviceSignaturePayload(
    val deviceId: String,
    val publicKey: String,
    val ts: String,
    val nonce: String
) {
    fun toCanonicalJson(): String = JsonSerialization.writeValueAsString(this)
}
