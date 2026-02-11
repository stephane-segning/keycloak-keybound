package com.ssegning.keycloak.keybound.examples.resource

import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

@Component
class BackendUserIdResolver {
    fun resolve(jwt: Jwt?): String? {
        if (jwt == null) {
            return null
        }

        val explicit = listOf("backend_user_id", "user_id")
            .mapNotNull { claim -> jwt.claims[claim] as? String }
            .firstOrNull { it.isNotBlank() }
        if (!explicit.isNullOrBlank()) {
            return explicit
        }

        val subject = jwt.subject?.trim().orEmpty()
        if (subject.isBlank()) {
            return null
        }

        if (subject.startsWith("f:")) {
            val parts = subject.split(":")
            if (parts.size >= 3 && parts.last().isNotBlank()) {
                return parts.last()
            }
        }

        return subject
    }
}
