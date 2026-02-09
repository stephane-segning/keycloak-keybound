package com.ssegning.keycloak.keybound.models

data class DeviceDescriptor(
    val deviceId: String,
    val jkt: String,
    val publicJwk: Map<String, Any>?,
    val platform: String?,
    val model: String?,
    val appVersion: String?
)
