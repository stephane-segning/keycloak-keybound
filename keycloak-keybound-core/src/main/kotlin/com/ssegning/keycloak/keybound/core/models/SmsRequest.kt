package com.ssegning.keycloak.keybound.core.models

data class SmsRequest(
    val realm: String?,
    val clientId: String?,
    val ipAddress: String?,
    val userAgent: String?,
    val sessionId: String?,
    val traceId: String?,
    val metadata: Map<String?, Any?>?
)
