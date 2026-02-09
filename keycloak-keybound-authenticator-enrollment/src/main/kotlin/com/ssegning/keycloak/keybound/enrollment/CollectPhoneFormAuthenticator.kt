package com.ssegning.keycloak.keybound.enrollment

import com.ssegning.keycloak.keybound.authentcator.AbstractAuthenticator
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.slf4j.LoggerFactory

class CollectPhoneFormAuthenticator : AbstractAuthenticator() {

    companion object {
        private val log = LoggerFactory.getLogger(CollectPhoneFormAuthenticator::class.java)
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val challenge = context.form()
            .createForm("enroll-collect-phone.ftl")
        context.challenge(challenge)
    }

    override fun action(context: AuthenticationFlowContext) {
        val formData = context.httpRequest.decodedFormParameters
        val phoneNumber = formData.getFirst("phone")?.trim()

        if (phoneNumber.isNullOrBlank() || !isValidE164(phoneNumber)) {
            val challenge = context.form()
                .setError("invalidPhoneNumber")
                .setAttribute("phone", phoneNumber)
                .createForm("enroll-collect-phone.ftl")
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge)
            return
        }

        context.authenticationSession.setAuthNote("phone_e164", phoneNumber)
        context.success()
    }

    private fun isValidE164(phoneNumber: String): Boolean = PHONE_NUMBER_UTILS.parse(phoneNumber, "")
        .let {
            PHONE_NUMBER_UTILS.isValidNumber(it)
        }
}
