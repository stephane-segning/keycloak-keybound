package com.ssegning.keycloak.keybound.core.models

data class DeviceLookupResult(
    val found: Boolean,
    val userId: String? = null,
    val device: DeviceRecord? = null,
    val publicJwk: Map<String, Any>? = null
)
