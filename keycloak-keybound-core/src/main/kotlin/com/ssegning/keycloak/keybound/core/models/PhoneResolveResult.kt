package com.ssegning.keycloak.keybound.core.models

data class PhoneResolveResult(
    val phoneNumber: String,
    val userExists: Boolean,
    val hasDeviceCredentials: Boolean,
    val enrollmentPath: EnrollmentPath,
    val userId: String? = null,
    val username: String? = null
)
