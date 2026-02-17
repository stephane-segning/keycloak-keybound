package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticator
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel
import org.slf4j.LoggerFactory

class RouteEnrollmentPathAuthenticator : AbstractAuthenticator() {
    companion object {
        private val log = LoggerFactory.getLogger(RouteEnrollmentPathAuthenticator::class.java)
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val authSession = context.authenticationSession
        val path =
            authSession
                .getAuthNote(KeyboundFlowNotes.ENROLLMENT_PATH_NOTE_NAME)
                ?.takeIf {
                    it == KeyboundFlowNotes.ENROLLMENT_PATH_APPROVAL || it == KeyboundFlowNotes.ENROLLMENT_PATH_OTP
                }
                ?: KeyboundFlowNotes.ENROLLMENT_PATH_OTP

        authSession.setAuthNote(KeyboundFlowNotes.ENROLLMENT_PATH_NOTE_NAME, path)
        log.debug("Enrollment flow routed to '{}'", path)
        context.success()
    }

    override fun action(context: AuthenticationFlowContext) {
        // No action needed for this authenticator
    }

    override fun configuredFor(
        session: KeycloakSession,
        realm: RealmModel,
        user: UserModel?,
    ): Boolean = true
}
