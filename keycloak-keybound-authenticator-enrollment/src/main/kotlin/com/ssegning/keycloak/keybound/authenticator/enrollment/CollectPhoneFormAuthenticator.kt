package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticator
import com.ssegning.keycloak.keybound.core.helper.maskPhone
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.slf4j.LoggerFactory

class CollectPhoneFormAuthenticator : AbstractAuthenticator() {
    companion object {
        private val log = LoggerFactory.getLogger(CollectPhoneFormAuthenticator::class.java)
        private const val PHONE_FORM_TEMPLATE = "enroll-collect-phone.ftl"
        private const val PHONE_FORM_FIELD = "phone"
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        log.debug("Presenting phone collection form")
        val existingPhone = context.authenticationSession.getAuthNote(KeyboundFlowNotes.PHONE_E164_NOTE_NAME)
        val challenge =
            context
                .form()
                .setAttribute(PHONE_FORM_FIELD, existingPhone)
                .createForm(PHONE_FORM_TEMPLATE)
        context.challenge(challenge)
    }

    override fun action(context: AuthenticationFlowContext) {
        val formData = context.httpRequest.decodedFormParameters
        val phoneNumber = formData.getFirst(PHONE_FORM_FIELD)?.trim()

        if (phoneNumber.isNullOrBlank() || !isValidE164(phoneNumber)) {
            log.debug("Invalid phone number submitted {}", maskPhone(phoneNumber))
            val challenge =
                context
                    .form()
                    .setError("invalidPhoneNumber")
                    .setAttribute(PHONE_FORM_FIELD, phoneNumber)
                    .createForm(PHONE_FORM_TEMPLATE)
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge)
            return
        }

        context.authenticationSession.setAuthNote(KeyboundFlowNotes.PHONE_E164_NOTE_NAME, phoneNumber)
        context.authenticationSession.removeAuthNote(KeyboundFlowNotes.PHONE_VERIFIED_NOTE_NAME)
        context.authenticationSession.removeAuthNote(KeyboundFlowNotes.ENROLL_SMS_HASH_NOTE_NAME)
        context.authenticationSession.removeAuthNote(KeyboundFlowNotes.BACKEND_USER_ID_NOTE_NAME)
        context.authenticationSession.removeAuthNote(KeyboundFlowNotes.RESOLVED_USERNAME_NOTE_NAME)
        context.authenticationSession.removeAuthNote(KeyboundFlowNotes.ENROLLMENT_PATH_NOTE_NAME)
        log.debug("Collected phone number {}", maskPhone(phoneNumber))
        context.success()
    }

    private fun isValidE164(phoneNumber: String): Boolean =
        try {
            PHONE_NUMBER_UTILS
                .parse(phoneNumber, "")
                .let {
                    PHONE_NUMBER_UTILS.isValidNumber(it)
                }
        } catch (e: Exception) {
            false
        }
}
