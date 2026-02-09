package com.ssegning.keycloak.keybound.authenticator

import com.ssegning.keycloak.keybound.core.AbstractAuthenticator
import com.ssegning.keycloak.keybound.helper.ATTEMPTED_PHONE_NUMBER
import com.ssegning.keycloak.keybound.helper.allowedCountries
import com.ssegning.keycloak.keybound.helper.phoneNumber
import com.ssegning.keycloak.keybound.service.formatE164
import org.jboss.logging.Logger
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.keycloak.authentication.authenticators.broker.AbstractIdpAuthenticator
import org.keycloak.models.DefaultActionTokenKey
import org.keycloak.models.UserModel

class PhoneNumberGetNumber : AbstractAuthenticator() {
    companion object {
        private val log: Logger = Logger.getLogger(PhoneNumberGetNumber::class.java)
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val existingUserId = context.authenticationSession.getAuthNote(AbstractIdpAuthenticator.EXISTING_USER_INFO)
        if (existingUserId != null) {
            val existingUser = AbstractIdpAuthenticator.getExistingUser(
                context.session,
                context.realm,
                context.authenticationSession
            )
            if (handlePreExistingUser(context, existingUser)) return
        }

        val actionTokenUserId =
            context.authenticationSession.getAuthNote(DefaultActionTokenKey.ACTION_TOKEN_USER_ID)

        if (actionTokenUserId != null) {
            val existingUser = context.session.users().getUserById(context.realm, actionTokenUserId)
            if (handlePreExistingUser(context, existingUser)) return
        }

        val challenge = context.form()
            .setAttribute("regionPrefix", "")
            .setAttribute("countries", context.allowedCountries())
            .createForm("request-user-phone-number.ftl")

        context.challenge(challenge)
    }

    override fun action(context: AuthenticationFlowContext) {
        val event = context.event
        val formData = context.httpRequest.decodedFormParameters

        if (formData.containsKey("cancel")) {
            context.attempted()
            return
        }

        var phoneNumberWithoutPrefix = formData.getFirst("phone").trim()
        val regionPrefix = formData.getFirst("regionPrefix")

        if (phoneNumberWithoutPrefix.isEmpty() || regionPrefix == null || regionPrefix.isEmpty()
        ) {
            event.error("missing_phone_number_or_region")
            val challenge = context.form()
                .setError("missing_phone_number_or_region")
                .setAttribute("regionPrefix", regionPrefix)
                .setAttribute("countries", context.allowedCountries())
                .createForm("request-user-phone-number.ftl")
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge)
            return
        }

        while (phoneNumberWithoutPrefix.startsWith("0")) {
            phoneNumberWithoutPrefix = phoneNumberWithoutPrefix.removePrefix("0")
        }

        val phoneNumber = (regionPrefix + phoneNumberWithoutPrefix).formatE164()

        if (phoneNumber.isNullOrEmpty()) {
            event.clone()
                .detail("phone_number", phoneNumber)
                .error("parse number error: Can't parse phone number")
            context.clearUser()

            val challenge = context
                .form()
                .setError("wrong_phone_number_or_region")
                .setAttribute("phoneNumber", phoneNumberWithoutPrefix)
                .setAttribute("regionPrefix", regionPrefix)
                .setAttribute("countries", context.allowedCountries())
                .createForm("request-user-phone-number.ftl")

            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge)
            return
        }

        context
            .authenticationSession
            .setAuthNote(ATTEMPTED_PHONE_NUMBER, phoneNumber)
        context.success()
    }

    private fun handlePreExistingUser(context: AuthenticationFlowContext, existingUser: UserModel): Boolean {
        val attrName = context.phoneNumber()

        val phoneNumbers = existingUser.getAttributeStream(attrName).toList()
        if (!phoneNumbers.isEmpty()) {
            val phoneNumber = phoneNumbers[0]

            log.debugf(
                "Forget-password triggered when re-authenticating user after first broker login. Pre-filling request-user-phone-number screen with user's phone '%s' ",
                phoneNumber
            )

            context.user = existingUser
            val challenge = context.form()
                .setAttribute("phoneNumber", phoneNumber)
                .setAttribute("countries", context.allowedCountries())
                .createForm("request-user-phone-number.ftl")

            context.challenge(challenge)
            return true
        }
        return false
    }
}