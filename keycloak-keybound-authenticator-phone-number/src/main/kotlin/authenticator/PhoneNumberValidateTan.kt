package com.ssegning.keycloak.keybound.authenticator

import com.ssegning.keycloak.keybound.core.AbstractAuthenticator
import com.ssegning.keycloak.keybound.helper.ATTEMPTED_HASH
import com.ssegning.keycloak.keybound.helper.ATTEMPTED_PHONE_NUMBER
import com.ssegning.keycloak.keybound.helper.phoneNumber
import com.ssegning.keycloak.keybound.helper.smsRequestContext
import com.ssegning.keycloak.keybound.spi.ApiGateway
import org.jboss.logging.Logger
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.events.Errors

class PhoneNumberValidateTan(val apiGateway: ApiGateway) : AbstractAuthenticator() {
    companion object {
        private val log: Logger = Logger.getLogger(PhoneNumberValidateTan::class.java)
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val authenticationSession = context.authenticationSession
        val phoneNumber = authenticationSession.getAuthNote(ATTEMPTED_PHONE_NUMBER)
        val hash = authenticationSession.getAuthNote(ATTEMPTED_HASH)

        if (hash == null || phoneNumber == null) {
            context.resetFlow()
            return
        }

        val challenge = context.form()
            .setAttribute("phoneNumber", phoneNumber)
            .createForm("request-user-tan-code.ftl")
        context.challenge(challenge)
    }

    override fun action(context: AuthenticationFlowContext) {
        val authenticationSession = context.authenticationSession
        val hash = authenticationSession.getAuthNote(ATTEMPTED_HASH)
        val phoneNumber = authenticationSession.getAuthNote(ATTEMPTED_PHONE_NUMBER)
        val realm = context.realm

        val formData = context.httpRequest.decodedFormParameters
        val cancel = formData.getFirst("cancel")
        if (cancel != null) {
            context.resetFlow()
            return
        }

        val code = formData.getFirst("code")
        val validate = apiGateway.confirmSmsCode(
            context,
            context.smsRequestContext("validate_tan"),
            phoneNumber,
            code,
            hash
        )

        if (!validate.isNullOrEmpty()) {
            var user = context.user

            if (user == null) {
                val attrName = context.phoneNumber()
                val userProvider = context.session.users()
                val users = userProvider.searchForUserByUserAttributeStream(realm, attrName, phoneNumber).toList()

                if (!users.isEmpty()) {
                    if (users.size > 1) {
                        log.warnf("Multiple users match %s=%s; using the first match", attrName, phoneNumber)
                    }
                    user = users[0]
                } else {
                    val userByUsername = userProvider.getUserByUsername(realm, phoneNumber)
                    user = userByUsername ?: userProvider.addUser(realm, phoneNumber)
                    user.isEnabled = true
                }

                if (!user!!.isEnabled) {
                    context.resetFlow()
                    return
                }

                user.setAttribute(attrName, mutableListOf<String?>(phoneNumber))
            }

            context.user = user
            context.success()
        } else {
            val event = context.event
            event.error(Errors.INVALID_CODE)

            val challenge = context
                .form()
                .setAttribute("phoneNumber", phoneNumber)
                .setAttribute("smsCode", code)
                .setError(Errors.INVALID_CODE)
                .createForm("request-user-tan-code.ftl")
            context.challenge(challenge)
        }
    }
}