package com.ssegning.keycloak.keybound.core.models

import kotlin.time.Instant

data class BackendUser(
    val userId: String,
    val realm: String? = null,
    val username: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val enabled: Boolean = true,
    val emailVerified: Boolean = false,
    val attributes: Map<String, String> = emptyMap(),
    val createdAt: Instant? = null,
)
