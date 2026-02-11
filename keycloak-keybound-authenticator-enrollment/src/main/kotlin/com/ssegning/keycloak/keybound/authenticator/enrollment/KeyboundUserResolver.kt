package com.ssegning.keycloak.keybound.authenticator.enrollment

import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.models.UserModel
import org.keycloak.storage.StorageId
import org.slf4j.LoggerFactory
import com.ssegning.keycloak.keybound.core.helper.maskForAttribute

object KeyboundUserResolver {
    private val log = LoggerFactory.getLogger(KeyboundUserResolver::class.java)

    fun resolveBackendUserId(user: UserModel): String {
        val backendAttributeId = user.getFirstAttribute("backend_user_id")?.trim()
        if (!backendAttributeId.isNullOrBlank()) {
            return backendAttributeId
        }
        return StorageId.externalId(user.id) ?: user.id
    }

    fun resolveUser(
        context: AuthenticationFlowContext,
        backendUserId: String?,
        username: String?,
        phoneE164: String?
    ): UserModel? {
        val realm = context.realm
        val users = context.session.users()

        if (!backendUserId.isNullOrBlank()) {
            val byBackendAttribute = findSingleUserByAttribute(context, "backend_user_id", backendUserId)
            if (byBackendAttribute != null) {
                return byBackendAttribute
            }
            val byId = users.getUserById(realm, backendUserId)
            if (byId != null) {
                return byId
            }
        }

        if (!username.isNullOrBlank()) {
            val byUsername = users.getUserByUsername(realm, username)
            if (byUsername != null) {
                return byUsername
            }
            val byEmail = users.getUserByEmail(realm, username)
            if (byEmail != null) {
                return byEmail
            }
        }

        if (!phoneE164.isNullOrBlank()) {
            return findSingleUserByAttribute(context, "phone_e164", phoneE164)
                ?: findSingleUserByAttribute(context, "phone_number", phoneE164)
                ?: users.getUserByUsername(realm, phoneE164)
                ?: users.getUserByEmail(realm, phoneE164)
        }

        return null
    }

    private fun findSingleUserByAttribute(
        context: AuthenticationFlowContext,
        attributeName: String,
        attributeValue: String
    ): UserModel? {
        val stream = context.session.users()
            .searchForUserByUserAttributeStream(context.realm, attributeName, attributeValue)
        return try {
            val iterator = stream.iterator()
            if (!iterator.hasNext()) {
                null
            } else {
                val first = iterator.next()
                if (iterator.hasNext()) {
                    log.error(
                        "Multiple users resolved for {}={} in realm {}",
                        attributeName,
                        maskForAttribute(attributeName, attributeValue),
                        context.realm.name
                    )
                    null
                } else {
                    first
                }
            }
        } finally {
            stream.close()
        }
    }
}
