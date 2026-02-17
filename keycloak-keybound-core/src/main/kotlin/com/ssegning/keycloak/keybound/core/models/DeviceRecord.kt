package com.ssegning.keycloak.keybound.core.models

import kotlin.time.Instant

data class DeviceRecord(
    val deviceId: String,
    val jkt: String,
    val status: DeviceStatus,
    val createdAt: Instant? = null,
    val label: String? = null,
)
