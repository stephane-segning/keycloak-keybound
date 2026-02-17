package com.ssegning.keycloak.keybound.core.models

data class PhoneResolveOrCreateResult(
    val phoneNumber: String,
    val userId: String,
    val username: String,
    val created: Boolean,
)
