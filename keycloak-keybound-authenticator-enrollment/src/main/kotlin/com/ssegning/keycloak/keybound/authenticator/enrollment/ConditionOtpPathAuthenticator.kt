package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticator
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticator
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel
import org.slf4j.LoggerFactory

class ConditionOtpPathAuthenticator :
    AbstractAuthenticator(),
    ConditionalAuthenticator {
    companion object {
        private val log = LoggerFactory.getLogger(ConditionOtpPathAuthenticator::class.java)
        val SINGLETON = ConditionOtpPathAuthenticator()
    }

    override fun matchCondition(context: AuthenticationFlowContext): Boolean {
        val selectedPath = context.authenticationSession.getAuthNote(KeyboundFlowNotes.ENROLLMENT_PATH_NOTE_NAME)
        val result = selectedPath == KeyboundFlowNotes.ENROLLMENT_PATH_OTP
        log.debug("Condition otp path match={} selectedPath={}", result, selectedPath)
        return result
    }

    override fun action(context: AuthenticationFlowContext) {
        // No action needed
    }

    override fun configuredFor(
        session: KeycloakSession,
        realm: RealmModel,
        user: UserModel?,
    ): Boolean = true
}
