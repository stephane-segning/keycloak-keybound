package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticator
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.keycloak.models.UserModel
import org.slf4j.LoggerFactory

/**
 * Authenticator that finds an existing user by verified phone.
 * If no user exists for the verified phone, a new user is created.
 */
class FindOrCreateUserAuthenticator : AbstractAuthenticator() {
    companion object {
        private val log = LoggerFactory.getLogger(FindOrCreateUserAuthenticator::class.java)
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val authSession = context.authenticationSession
        val phoneVerified = authSession.getAuthNote(KeyboundFlowNotes.PHONE_VERIFIED_NOTE_NAME) == "true"
        val phoneE164 = authSession.getAuthNote(KeyboundFlowNotes.PHONE_E164_NOTE_NAME)?.trim()
        if (!phoneVerified || phoneE164.isNullOrBlank()) {
            log.warn("Cannot resolve user without verified phone")
            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS)
            return
        }

        log.debug("Resolving or creating user from verified phone '{}'", phoneE164)
        var user = resolveByVerifiedPhone(context, phoneE164)
        if (user == null) {
            user = createUserFromPhone(context, phoneE164)
        }

        if (user == null) {
            log.warn("Unable to resolve or create user from verified phone")
            context.failure(AuthenticationFlowError.UNKNOWN_USER)
            return
        }

        log.debug("Resolved user {} for enrollment", user.username)
        context.user = user
        context.success()
    }

    override fun action(context: AuthenticationFlowContext) {
        // No action needed for this authenticator
    }

    private fun resolveByVerifiedPhone(context: AuthenticationFlowContext, phoneE164: String): UserModel? {
        val session = context.session
        val realm = context.realm
        return findSingleUserByAttribute(context, "phone_e164", phoneE164)
            ?: findSingleUserByAttribute(context, "phone_number", phoneE164)
            ?: session.users().getUserByUsername(realm, phoneE164)
            ?: session.users().getUserByEmail(realm, phoneE164)
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
                        "Multiple users resolved for {}='{}' in realm {}",
                        attributeName,
                        attributeValue,
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

    private fun createUserFromPhone(context: AuthenticationFlowContext, phoneE164: String): UserModel? {
        val normalizedPhone = phoneE164.trim()
        return try {
            val createdUser = context.session.users().addUser(context.realm, normalizedPhone)
            if (createdUser == null) {
                log.error("Failed to create user from verified phone {}", normalizedPhone)
                null
            } else {
                createdUser.isEnabled = true
                createdUser.setSingleAttribute("phone_e164", normalizedPhone)
                log.debug("Created new user '{}' from verified phone '{}'", createdUser.username, normalizedPhone)
                createdUser
            }
        } catch (exception: Exception) {
            log.error("Error creating user from verified phone {}", normalizedPhone, exception)
            null
        }
    }
}
