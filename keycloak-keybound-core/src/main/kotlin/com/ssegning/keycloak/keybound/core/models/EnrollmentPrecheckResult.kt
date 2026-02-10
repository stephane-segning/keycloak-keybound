package com.ssegning.keycloak.keybound.core.models

data class EnrollmentPrecheckResult(
    val decision: EnrollmentDecision,
    val reason: String? = null,
    val boundUserId: String? = null,
    val retryAfterSeconds: Int? = null
)
