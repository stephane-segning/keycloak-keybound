package com.ssegning.keycloak.keybound.core.models

data class ApprovalPollingTokenClaims(
    val realm: String,
    val client_id: String,
    val aud: String,
    val sid: String,
    val sub: String?,
    val request_id: String,
    val iat: Long,
    val nbf: Long,
    val jti: String,
    val exp: Long
)
