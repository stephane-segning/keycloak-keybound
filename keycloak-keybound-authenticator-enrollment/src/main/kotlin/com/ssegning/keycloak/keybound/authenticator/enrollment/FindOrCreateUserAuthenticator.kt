package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticator
import com.ssegning.keycloak.keybound.core.helper.getApi
import com.ssegning.keycloak.keybound.core.helper.maskPhone
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
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

        val resolved = context.session.getApi().resolveOrCreateUserByPhone(context, phoneE164)
        if (resolved == null) {
            log.error("Backend failed to resolve or create user from phone {}", maskPhone(phoneE164))
            context.failure(AuthenticationFlowError.INTERNAL_ERROR)
            return
        }

        authSession.setAuthNote(KeyboundFlowNotes.BACKEND_USER_ID_NOTE_NAME, resolved.userId)
        authSession.setAuthNote(KeyboundFlowNotes.RESOLVED_USERNAME_NOTE_NAME, resolved.username)

        log.debug("Resolving Keycloak user for backend userId={}", resolved.userId)
        val user =
            KeyboundUserResolver.resolveUser(
                context = context,
                backendUserId = resolved.userId,
                username = resolved.username,
                phoneE164 = phoneE164,
            )

        if (user == null) {
            log.warn("Unable to map backend user '{}' to Keycloak user context", resolved.userId)
            context.failure(AuthenticationFlowError.UNKNOWN_USER)
            return
        }

        log.debug("Resolved user for enrollment")
        context.user = user
        context.success()
    }

    override fun action(context: AuthenticationFlowContext) {
        // No action needed for this authenticator
    }
}
