package com.ssegning.keycloak.keybound.core.models

import com.fasterxml.jackson.annotation.JsonProperty

data class DeviceCredentialData(
    @param:JsonProperty("device_id")
    val deviceId: String,
    @param:JsonProperty("device_os")
    val deviceOs: String,
    @param:JsonProperty("device_model")
    val deviceModel: String,
    @param:JsonProperty("created_at")
    val createdAt: Long,
)
