package com.ssegning.keycloak.keybound.enrollment

import com.ssegning.keycloak.keybound.authentcator.AbstractAuthenticator
import org.keycloak.authentication.AuthenticationFlowContext
import org.slf4j.LoggerFactory

class FindOrCreateUserAuthenticator : AbstractAuthenticator() {
    companion object {
        private val log = LoggerFactory.getLogger(FindOrCreateUserAuthenticator::class.java)
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val phoneE164 = context.authenticationSession.getAuthNote("phone_e164")

        if (phoneE164 == null) {
            context.attempted()
            return
        }

        val realm = context.realm
        val session = context.session
        var user = session.users().getUserByUsername(realm, phoneE164)

        if (user == null) {
            user = session.users().addUser(realm, phoneE164)
            user.isEnabled = true
            user.setSingleAttribute("phone", phoneE164)
            user.setSingleAttribute("created_by", "enrollment")
        }

        context.user = user
        context.success()
    }

    override fun action(context: AuthenticationFlowContext) {
        // No action needed for this authenticator
    }
}
