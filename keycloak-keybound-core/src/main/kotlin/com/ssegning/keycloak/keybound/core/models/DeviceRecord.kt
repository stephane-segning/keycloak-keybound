package com.ssegning.keycloak.keybound.core.models

import java.time.OffsetDateTime

data class DeviceRecord(
    val deviceId: String,
    val jkt: String,
    val status: DeviceStatus,
    val createdAt: OffsetDateTime? = null,
    val label: String? = null
)
