package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticator
import com.ssegning.keycloak.keybound.core.helper.getApi
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
        val user = context.user
        val path = if (user != null && hasRegisteredDevices(context, user)) {
            KeyboundFlowNotes.ENROLLMENT_PATH_APPROVAL
        } else {
            KeyboundFlowNotes.ENROLLMENT_PATH_OTP
        }

        context.authenticationSession.setAuthNote(KeyboundFlowNotes.ENROLLMENT_PATH_NOTE_NAME, path)
        log.debug("Enrollment flow routed to '{}' (user={})", path, user?.username)
        context.success()
    }

    override fun action(context: AuthenticationFlowContext) {
        // No action needed for this authenticator
    }

    override fun configuredFor(session: KeycloakSession, realm: RealmModel, user: UserModel?): Boolean = true

    private fun hasRegisteredDevices(context: AuthenticationFlowContext, user: UserModel): Boolean {
        val devices = try {
            context.session.getApi().listUserDevices(user.id, false)
        } catch (exception: Exception) {
            log.error("Failed to load devices for user {}", user.id, exception)
            null
        }
        return !devices.isNullOrEmpty()
    }
}
