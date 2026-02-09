package com.ssegning.keycloak.keybound.authenticator

import com.ssegning.keycloak.keybound.core.AbstractAuthenticator
import com.ssegning.keycloak.keybound.helper.ATTEMPTED_HASH
import com.ssegning.keycloak.keybound.helper.ATTEMPTED_PHONE_NUMBER
import com.ssegning.keycloak.keybound.helper.smsRequestContext
import com.ssegning.keycloak.keybound.spi.ApiGateway
import org.jboss.logging.Logger
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError

class PhoneNumberSendTan(val apiGateway: ApiGateway) : AbstractAuthenticator() {
    companion object {
        private val log: Logger = Logger.getLogger(PhoneNumberSendTan::class.java)
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val authenticationSession = context.authenticationSession
        val phoneNumber = authenticationSession.getAuthNote(ATTEMPTED_PHONE_NUMBER)
        val event = context.event

        val hash = apiGateway.sendSmsAndGetHash(
            context,
            context.smsRequestContext("send_tan"),
            phoneNumber,
        )

        if (hash.isNullOrEmpty()) {
            val challenge = context
                .form()
                .setError("sms_could_not_be_sent")
                .createForm("sms-tan-not-send-error.ftl")

            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge)
            return
        }

        event.success()

        context
            .authenticationSession
            .setAuthNote(ATTEMPTED_HASH, hash)
        context.success()
    }

    override fun action(context: AuthenticationFlowContext) {
        val formData = context.httpRequest.decodedFormParameters
        val cancel = formData.getFirst("cancel")

        if (cancel != null) {
            context.resetFlow()
        }
    }
}