package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticator
import com.ssegning.keycloak.keybound.core.helper.getApi
import com.ssegning.keycloak.keybound.core.models.SmsRequest
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.slf4j.LoggerFactory
import java.security.SecureRandom

class SendValidateOtpAuthenticator : AbstractAuthenticator() {
    companion object {
        private val log = LoggerFactory.getLogger(SendValidateOtpAuthenticator::class.java)
        private const val VERIFY_FORM_TEMPLATE = "enroll-verify-phone.ftl"
        private const val OTP_FORM_FIELD = "otp"
        private const val PHONE_FORM_ATTRIBUTE = "phone"
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val authSession = context.authenticationSession
        val phoneNumber = authSession.getAuthNote(KeyboundFlowNotes.PHONE_E164_NOTE_NAME)?.trim()
        if (phoneNumber.isNullOrBlank()) {
            log.error("Cannot send OTP without collected phone number in session")
            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS)
            return
        }

        val phoneForChallenge = authSession.getAuthNote(KeyboundFlowNotes.ENROLL_PHONE_NOTE_NAME)
        val smsHash = authSession.getAuthNote(KeyboundFlowNotes.ENROLL_SMS_HASH_NOTE_NAME)
        if (phoneForChallenge == phoneNumber && !smsHash.isNullOrBlank()) {
            context.challenge(
                context.form()
                    .setAttribute(PHONE_FORM_ATTRIBUTE, phoneNumber)
                    .createForm(VERIFY_FORM_TEMPLATE)
            )
            return
        }

        val otp = generateOtp()
        val smsRequest = SmsRequest(
            realm = context.realm.name,
            clientId = authSession.client.clientId,
            ipAddress = context.connection.remoteAddr,
            userAgent = context.httpRequest.httpHeaders.getRequestHeader("User-Agent").firstOrNull(),
            sessionId = authSession.parentSession.id,
            traceId = null,
            metadata = mutableMapOf("otp" to otp)
        )

        val apiGateway = context.session.getApi()
        val hash = try {
            apiGateway.sendSmsAndGetHash(context, smsRequest, phoneNumber)
        } catch (e: Exception) {
            log.error("Failed to send OTP SMS for phone {}", phoneNumber, e)
            null
        }

        if (hash.isNullOrBlank()) {
            val challenge = context.form()
                .setError("smsSendError")
                .setAttribute(PHONE_FORM_ATTRIBUTE, phoneNumber)
                .createForm(VERIFY_FORM_TEMPLATE)
            context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR, challenge)
            return
        }

        authSession.setAuthNote(KeyboundFlowNotes.ENROLL_PHONE_NOTE_NAME, phoneNumber)
        authSession.setAuthNote(KeyboundFlowNotes.ENROLL_SMS_HASH_NOTE_NAME, hash)
        authSession.removeAuthNote(KeyboundFlowNotes.PHONE_VERIFIED_NOTE_NAME)

        log.debug("OTP challenge sent for phone {}", phoneNumber)
        context.challenge(
            context.form()
                .setAttribute(PHONE_FORM_ATTRIBUTE, phoneNumber)
                .createForm(VERIFY_FORM_TEMPLATE)
        )
    }

    override fun action(context: AuthenticationFlowContext) {
        val authSession = context.authenticationSession
        val submittedOtp = context.httpRequest.decodedFormParameters.getFirst(OTP_FORM_FIELD)?.trim()
        val phoneNumber = authSession.getAuthNote(KeyboundFlowNotes.ENROLL_PHONE_NOTE_NAME)?.trim()
        val smsHash = authSession.getAuthNote(KeyboundFlowNotes.ENROLL_SMS_HASH_NOTE_NAME)?.trim()

        if (submittedOtp.isNullOrBlank() || phoneNumber.isNullOrBlank() || smsHash.isNullOrBlank()) {
            log.error(
                "OTP submission context missing: otp_present={} phone_present={} hash_present={}",
                !submittedOtp.isNullOrBlank(),
                !phoneNumber.isNullOrBlank(),
                !smsHash.isNullOrBlank()
            )
            context.failure(AuthenticationFlowError.INTERNAL_ERROR)
            return
        }

        val smsRequest = SmsRequest(
            realm = context.realm.name,
            clientId = authSession.client.clientId,
            ipAddress = context.connection.remoteAddr,
            userAgent = context.httpRequest.httpHeaders.getRequestHeader("User-Agent").firstOrNull(),
            sessionId = authSession.parentSession.id,
            traceId = null,
            metadata = mutableMapOf()
        )

        val confirmed = try {
            context.session.getApi().confirmSmsCode(
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

        if (!confirmed) {
            val challenge = context.form()
                .setError("invalidOtp")
                .setAttribute(PHONE_FORM_ATTRIBUTE, phoneNumber)
                .createForm(VERIFY_FORM_TEMPLATE)
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge)
            return
        }

        authSession.setAuthNote(KeyboundFlowNotes.PHONE_E164_NOTE_NAME, phoneNumber)
        authSession.setAuthNote(KeyboundFlowNotes.PHONE_VERIFIED_NOTE_NAME, "true")
        authSession.removeAuthNote(KeyboundFlowNotes.ENROLL_PHONE_NOTE_NAME)
        authSession.removeAuthNote(KeyboundFlowNotes.ENROLL_SMS_HASH_NOTE_NAME)

        log.debug("OTP verified for phone {}", phoneNumber)
        context.success()
    }

    private fun generateOtp(): String {
        val random = SecureRandom()
        val num = random.nextInt(1_000_000)
        return String.format("%06d", num)
    }
}
