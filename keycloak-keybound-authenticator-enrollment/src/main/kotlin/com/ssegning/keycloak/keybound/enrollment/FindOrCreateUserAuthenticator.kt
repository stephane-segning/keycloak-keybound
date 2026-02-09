package com.ssegning.keycloak.keybound.enrollment

import com.ssegning.keycloak.keybound.authentcator.AbstractAuthenticator
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.slf4j.LoggerFactory

/**
 * Authenticator that finds an existing user by phone number.
 * Note: Despite the name, this authenticator no longer creates users.
 */
class FindOrCreateUserAuthenticator : AbstractAuthenticator() {
    companion object {
        private val log = LoggerFactory.getLogger(FindOrCreateUserAuthenticator::class.java)
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val phoneVerified = context.authenticationSession.getAuthNote("phone_verified")
        if (phoneVerified != "true") {
            context.attempted()
            return
        }

        val phoneE164 = context.authenticationSession.getAuthNote("phone_e164")

        if (phoneE164 == null) {
            context.attempted()
            return
        }

        val realm = context.realm
        val session = context.session
        val user = session.users().getUserByUsername(realm, phoneE164)

        if (user == null) {
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
