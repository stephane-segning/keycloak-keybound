package com.ssegning.keycloak.keybound.core.models

import com.fasterxml.jackson.annotation.JsonProperty

data class DeviceCredentialData(
    @JsonProperty("device_id")
    val deviceId: String,
    @JsonProperty("device_os")
    val deviceOs: String,
    @JsonProperty("device_model")
    val deviceModel: String,
    @JsonProperty("created_at")
    val createdAt: Long
)
