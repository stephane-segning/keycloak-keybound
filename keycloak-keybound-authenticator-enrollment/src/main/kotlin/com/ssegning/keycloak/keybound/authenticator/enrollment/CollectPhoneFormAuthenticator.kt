package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.authenticator.enrollment.authenticator.AbstractKeyAuthenticator
import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticator
import com.ssegning.keycloak.keybound.core.helper.getApi
import com.ssegning.keycloak.keybound.core.models.SmsRequest
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import jakarta.ws.rs.core.MultivaluedMap
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.slf4j.LoggerFactory
import java.security.SecureRandom

class CollectPhoneFormAuthenticator : AbstractAuthenticator() {

    companion object {
        private val log = LoggerFactory.getLogger(CollectPhoneFormAuthenticator::class.java)
        private const val ENROLL_PHONE_NOTE = "enroll.phone_e164"
        private const val ENROLL_SMS_HASH_NOTE = "enroll.sms_hash"
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val userHint = context.authenticationSession.getAuthNote(AbstractKeyAuthenticator.USER_HINT_NOTE_NAME)?.trim()
        if (!userHint.isNullOrBlank()) {
            val hintedUser = context.session.users().getUserByUsername(context.realm, userHint)
                ?: context.session.users().getUserByEmail(context.realm, userHint)
            if (hintedUser != null) {
                log.debug(
                    "Skipping phone collection because user_hint '{}' resolved to user '{}'",
                    userHint,
                    hintedUser.username
                )
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

        val smsRequest = SmsRequest(
            realm = context.realm.name,
            clientId = context.authenticationSession.client.clientId,
            ipAddress = context.connection.remoteAddr,
            userAgent = context.httpRequest.httpHeaders.getRequestHeader("User-Agent").firstOrNull(),
            sessionId = context.authenticationSession.parentSession.id,
            traceId = null,
            metadata = mutableMapOf("otp" to otp)
        )

        val apiGateway = context.session.getApi()

        try {
            log.debug("Sending enrollment SMS to {}", phoneNumber)
            val hash = apiGateway.sendSmsAndGetHash(context, smsRequest, phoneNumber)
            if (hash.isNullOrBlank()) {
                log.error("Backend returned empty SMS hash for {}", phoneNumber)
                val challenge = context.form()
                    .setError("smsSendError")
                    .setAttribute("phone", phoneNumber)
                    .createForm("enroll-collect-phone.ftl")
                context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR, challenge)
                return
            }
            context.authenticationSession.setAuthNote(ENROLL_PHONE_NOTE, phoneNumber)
            context.authenticationSession.setAuthNote(ENROLL_SMS_HASH_NOTE, hash)
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
        val phoneNumber = context.authenticationSession.getAuthNote(ENROLL_PHONE_NOTE)
        val smsHash = context.authenticationSession.getAuthNote(ENROLL_SMS_HASH_NOTE)

        if (submittedOtp.isNullOrBlank() || phoneNumber.isNullOrBlank() || smsHash.isNullOrBlank()) {
            log.error(
                "Missing OTP submission context otp_present={} phone_present={} hash_present={}",
                !submittedOtp.isNullOrBlank(),
                !phoneNumber.isNullOrBlank(),
                !smsHash.isNullOrBlank()
            )
            val challenge = context.form()
                .setError("invalidOtp")
                .setAttribute("phone", phoneNumber)
                .createForm("enroll-verify-phone.ftl")
            context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR, challenge)
            return
        }

        val smsRequest = SmsRequest(
            realm = context.realm.name,
            clientId = context.authenticationSession.client.clientId,
            ipAddress = context.connection.remoteAddr,
            userAgent = context.httpRequest.httpHeaders.getRequestHeader("User-Agent").firstOrNull(),
            sessionId = context.authenticationSession.parentSession.id,
            traceId = null,
            metadata = mutableMapOf()
        )

        val apiGateway = context.session.getApi()

        val confirmed = try {
            apiGateway.confirmSmsCode(
                context = context,
                request = smsRequest,
                phoneNumber = phoneNumber,
                code = submittedOtp,
                hash = smsHash
            )?.toBooleanStrictOrNull() == true
        } catch (e: Exception) {
            log.error("OTP confirmation failed for phone {}", phoneNumber, e)
            false
        }

        if (confirmed) {
            log.debug("OTP verified through ApiGateway for phone {}", phoneNumber)
            context.authenticationSession.setAuthNote("phone_verified", "true")
            context.authenticationSession.setAuthNote("phone_e164", phoneNumber)
            context.authenticationSession.removeAuthNote(ENROLL_PHONE_NOTE)
            context.authenticationSession.removeAuthNote(ENROLL_SMS_HASH_NOTE)
            context.success()
        } else {
            log.debug("OTP verification rejected through ApiGateway for phone {}", phoneNumber)
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

    private fun isValidE164(phoneNumber: String): Boolean = try {
        PHONE_NUMBER_UTILS.parse(phoneNumber, "")
            .let {
                PHONE_NUMBER_UTILS.isValidNumber(it)
            }
    } catch (e: Exception) {
        false
    }
}
