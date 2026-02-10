package com.ssegning.keycloak.keybound.core.models

import com.fasterxml.jackson.annotation.JsonProperty

data class DeviceSecretData(
    @param:JsonProperty("public_key")
    val publicKey: String,
    @param:JsonProperty("jkt")
    val jkt: String
)
