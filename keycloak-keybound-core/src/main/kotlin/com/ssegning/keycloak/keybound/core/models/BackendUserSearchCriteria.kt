package com.ssegning.keycloak.keybound.core.models

data class BackendUserSearchCriteria(
    val search: String? = null,
    val username: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val enabled: Boolean? = null,
    val emailVerified: Boolean? = null,
    val exact: Boolean? = null,
    val attributes: Map<String, String>? = null,
    val firstResult: Int? = null,
    val maxResults: Int? = null
)
