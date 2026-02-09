package com.ssegning.keycloak.keybound.authenticator

import com.ssegning.keycloak.keybound.core.AbstractAuthenticator
import com.ssegning.keycloak.keybound.helper.ATTEMPTED_PHONE_NUMBER
import com.ssegning.keycloak.keybound.service.formatE164
import org.jboss.logging.Logger
import org.keycloak.authentication.AuthenticationFlowContext

class PhoneNumberConfirmNumber : AbstractAuthenticator() {
    companion object {
        private val log: Logger = Logger.getLogger(PhoneNumberConfirmNumber::class.java)
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val existingUser = context.user

        if (existingUser != null) {
            context.success()
            return
        }

        val authenticationSession = context.authenticationSession
        val phoneNumber = authenticationSession
            .getAuthNote(ATTEMPTED_PHONE_NUMBER)
            .formatE164()!!

        val challenge = context
            .form()
            .setAttribute("phoneNumber", phoneNumber)
            .createForm("confirm-user-phone-number.ftl")

        context.challenge(challenge)
    }

    override fun action(context: AuthenticationFlowContext) {
        val formData = context
            .httpRequest
            .decodedFormParameters

        val cancel = formData.getFirst("cancel")

        if (cancel != null) {
            context.resetFlow()
            return
        }

        context.success()
    }
}