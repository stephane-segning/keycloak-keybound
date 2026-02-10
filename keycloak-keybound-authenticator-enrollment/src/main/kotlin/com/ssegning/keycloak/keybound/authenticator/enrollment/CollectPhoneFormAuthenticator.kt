package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.authenticator.enrollment.authenticator.AbstractKeyAuthenticator
import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticator
import com.ssegning.keycloak.keybound.core.helper.getApi
import com.ssegning.keycloak.keybound.core.models.SmsRequest
import jakarta.ws.rs.core.MultivaluedMap
import java.security.SecureRandom
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.slf4j.LoggerFactory

class CollectPhoneFormAuthenticator : AbstractAuthenticator() {

    companion object {
        private val log = LoggerFactory.getLogger(CollectPhoneFormAuthenticator::class.java)
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val userHint = context.authenticationSession.getAuthNote(AbstractKeyAuthenticator.USER_HINT_NOTE_NAME)?.trim()
        if (!userHint.isNullOrBlank()) {
            val hintedUser = context.session.users().getUserByUsername(context.realm, userHint)
                ?: context.session.users().getUserByEmail(context.realm, userHint)
            if (hintedUser != null) {
                log.debug("Skipping phone collection because user_hint '{}' resolved to user '{}'", userHint, hintedUser.username)
                context.success()
                return
            }
            log.debug("user_hint '{}' was provided but not resolved; falling back to phone collection", userHint)
        }

        log.debug("Presenting phone collection form")
        val challenge = context.form()
            .createForm("enroll-collect-phone.ftl")
        context.challenge(challenge)
    }

    override fun action(context: AuthenticationFlowContext) {
        val formData = context.httpRequest.decodedFormParameters

        if (formData.containsKey("otp")) {
            log.debug("Handling OTP submission")
            handleOtpSubmission(context, formData)
            return
        }

        val phoneNumber = formData.getFirst("phone")?.trim()

        if (phoneNumber.isNullOrBlank() || !isValidE164(phoneNumber)) {
            log.debug("Invalid phone number submitted: {}", phoneNumber)
            val challenge = context.form()
                .setError("invalidPhoneNumber")
                .setAttribute("phone", phoneNumber)
                .createForm("enroll-collect-phone.ftl")
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge)
            return
        }

        val otp = generateOtp()
        context.authenticationSession.setAuthNote("enroll.otp", otp)
        context.authenticationSession.setAuthNote("enroll.phone_e164", phoneNumber)

        val smsRequest = SmsRequest(
            realm = context.realm.name,
            clientId = context.authenticationSession.client.clientId,
            ipAddress = context.connection.remoteAddr,
            userAgent = context.httpRequest.httpHeaders.getRequestHeader("User-Agent").firstOrNull(),
            sessionId = context.authenticationSession.parentSession.id,
            traceId = null,
            metadata = mutableMapOf("otp" to otp)
        )

        try {
            log.debug("Sending enrollment SMS to {}", phoneNumber)
            context.session.getApi().sendSmsAndGetHash(context, smsRequest, phoneNumber)
        } catch (e: Exception) {
            log.error("Failed to send SMS to {}", phoneNumber, e)
            val challenge = context.form()
                .setError("smsSendError")
                .setAttribute("phone", phoneNumber)
                .createForm("enroll-collect-phone.ftl")
            context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR, challenge)
            return
        }

        val challenge = context.form()
            .setAttribute("phone", phoneNumber)
            .createForm("enroll-verify-phone.ftl")
        context.challenge(challenge)
    }

    private fun handleOtpSubmission(context: AuthenticationFlowContext, formData: MultivaluedMap<String, String>) {
        val submittedOtp = formData.getFirst("otp")?.trim()
        val storedOtp = context.authenticationSession.getAuthNote("enroll.otp")
        val phoneNumber = context.authenticationSession.getAuthNote("enroll.phone_e164")

        if (submittedOtp != null && submittedOtp == storedOtp) {
            log.debug("OTP verified for phone {}", phoneNumber)
            context.authenticationSession.setAuthNote("phone_verified", "true")
            context.authenticationSession.setAuthNote("phone_e164", phoneNumber)
            context.authenticationSession.removeAuthNote("enroll.otp")
            context.authenticationSession.removeAuthNote("enroll.phone_e164")
            context.success()
        } else {
            log.debug("OTP verification failed for phone {}", phoneNumber)
            val challenge = context.form()
                .setError("invalidOtp")
                .setAttribute("phone", phoneNumber)
                .createForm("enroll-verify-phone.ftl")
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge)
        }
    }

    private fun generateOtp(): String {
        val random = SecureRandom()
        val num = random.nextInt(1_000_000)
        return String.format("%06d", num)
    }

    private fun isValidE164(phoneNumber: String): Boolean = PHONE_NUMBER_UTILS.parse(phoneNumber, "")
        .let {
            PHONE_NUMBER_UTILS.isValidNumber(it)
        }
}
