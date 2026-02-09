package com.ssegning.keycloak.keybound.core.models

import com.fasterxml.jackson.annotation.JsonProperty

data class DeviceSecretData(
    @JsonProperty("public_key")
    val publicKey: String,
    @JsonProperty("jkt")
    val jkt: String
)
