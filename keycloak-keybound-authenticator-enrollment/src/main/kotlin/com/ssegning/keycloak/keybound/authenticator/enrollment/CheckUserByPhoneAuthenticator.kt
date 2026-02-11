package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticator
import com.ssegning.keycloak.keybound.core.helper.getApi
import org.keycloak.authentication.AuthenticationFlowContext
import org.slf4j.LoggerFactory

class CheckUserByPhoneAuthenticator : AbstractAuthenticator() {
    companion object {
        private val log = LoggerFactory.getLogger(CheckUserByPhoneAuthenticator::class.java)
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val authSession = context.authenticationSession
        val phoneE164 = authSession.getAuthNote(KeyboundFlowNotes.PHONE_E164_NOTE_NAME)?.trim()
        if (phoneE164.isNullOrBlank()) {
            log.debug("Phone note missing; skipping user lookup")
            context.success()
            return
        }

        val resolved = context.session.getApi().resolveUserByPhone(context, phoneE164)
        if (resolved == null) {
            log.error("Backend phone resolve failed for phone {}", phoneE164)
            context.failure(org.keycloak.authentication.AuthenticationFlowError.INTERNAL_ERROR)
            return
        }

        authSession.setAuthNote(
            KeyboundFlowNotes.ENROLLMENT_PATH_NOTE_NAME,
            if (resolved.enrollmentPath == com.ssegning.keycloak.keybound.core.models.EnrollmentPath.APPROVAL) {
                KeyboundFlowNotes.ENROLLMENT_PATH_APPROVAL
            } else {
                KeyboundFlowNotes.ENROLLMENT_PATH_OTP
            }
        )

        if (!resolved.userId.isNullOrBlank()) {
            authSession.setAuthNote(KeyboundFlowNotes.BACKEND_USER_ID_NOTE_NAME, resolved.userId)
        }
        if (!resolved.username.isNullOrBlank()) {
            authSession.setAuthNote(KeyboundFlowNotes.RESOLVED_USERNAME_NOTE_NAME, resolved.username)
        }

        val resolvedUser = KeyboundUserResolver.resolveUser(
            context = context,
            backendUserId = resolved.userId,
            username = resolved.username,
            phoneE164 = phoneE164
        )
        if (resolvedUser != null) {
            log.debug("Resolved existing user {} by phone {}", resolvedUser.username, phoneE164)
            context.user = resolvedUser
        } else {
            log.debug("No Keycloak user resolved for phone {} (backend user_exists={})", phoneE164, resolved.userExists)
        }

        context.success()
    }

    override fun action(context: AuthenticationFlowContext) {
        // No action needed for this authenticator
    }
}
