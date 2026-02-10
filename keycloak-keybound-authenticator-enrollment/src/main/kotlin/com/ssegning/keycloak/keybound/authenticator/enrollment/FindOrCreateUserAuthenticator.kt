package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.authenticator.enrollment.authenticator.AbstractKeyAuthenticator
import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticator
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.keycloak.models.UserModel
import org.slf4j.LoggerFactory

/**
 * Authenticator that finds an existing user through one of:
 * 1) explicit user hint in signed redirect parameters
 * 2) verified phone number collected by the OTP step
 */
class FindOrCreateUserAuthenticator : AbstractAuthenticator() {
    companion object {
        private val log = LoggerFactory.getLogger(FindOrCreateUserAuthenticator::class.java)
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val realm = context.realm
        val session = context.session
        val authSession = context.authenticationSession
        val userHint = authSession.getAuthNote(AbstractKeyAuthenticator.USER_HINT_NOTE_NAME)?.trim()
        val phoneVerified = authSession.getAuthNote("phone_verified") == "true"
        val phoneE164 = authSession.getAuthNote("phone_e164")?.trim()

        log.debug("Resolving user with hint='{}' phone_verified={} phone='{}'", userHint, phoneVerified, phoneE164)
        var user = resolveByHint(context, userHint)
            ?: resolveByVerifiedPhone(context, phoneVerified, phoneE164)

        if (user == null && phoneVerified && !phoneE164.isNullOrBlank()) {
            user = createUserFromPhone(context, phoneE164)
        }

        if (user == null) {
            log.warn("Unable to resolve user from user_hint or verified phone")
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

    private fun resolveByHint(context: AuthenticationFlowContext, userHint: String?): UserModel? {
        if (userHint.isNullOrBlank()) {
            return null
        }

        val session = context.session
        val realm = context.realm
        return session.users().getUserByUsername(realm, userHint)
            ?: session.users().getUserByEmail(realm, userHint)
            ?: findSingleUserByAttribute(context, "user_id", userHint)
            ?: findSingleUserByAttribute(context, "backend_user_id", userHint)
    }

    private fun resolveByVerifiedPhone(
        context: AuthenticationFlowContext,
        phoneVerified: Boolean,
        phoneE164: String?
    ): UserModel? {
        if (!phoneVerified || phoneE164.isNullOrBlank()) {
            return null
        }

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
