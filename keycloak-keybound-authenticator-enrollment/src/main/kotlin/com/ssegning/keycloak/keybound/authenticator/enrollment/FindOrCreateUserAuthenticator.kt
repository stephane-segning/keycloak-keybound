package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.authenticator.enrollment.authenticator.AbstractKeyAuthenticator
import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticator
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
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

        val user = when {
            !userHint.isNullOrBlank() -> {
                session.users().getUserByUsername(realm, userHint)
                    ?: session.users().getUserByEmail(realm, userHint)
            }

            phoneVerified && !phoneE164.isNullOrBlank() -> {
                session.users().getUserByUsername(realm, phoneE164)
            }

            else -> null
        }

        if (user == null) {
            log.warn("Unable to resolve user from user_hint or verified phone")
            context.failure(AuthenticationFlowError.UNKNOWN_USER)
            return
        }

        context.user = user
        context.success()
    }

    override fun action(context: AuthenticationFlowContext) {
        // No action needed for this authenticator
    }
}
