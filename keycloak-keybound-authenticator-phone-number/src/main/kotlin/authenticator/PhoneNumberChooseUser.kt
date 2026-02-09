package com.ssegning.keycloak.keybound.authenticator

import com.ssegning.keycloak.keybound.core.AbstractAuthenticator
import com.ssegning.keycloak.keybound.helper.ATTEMPTED_PHONE_NUMBER
import com.ssegning.keycloak.keybound.helper.noop
import com.ssegning.keycloak.keybound.helper.phoneNumber
import org.jboss.logging.Logger
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.events.Errors

class PhoneNumberChooseUser : AbstractAuthenticator() {
    companion object {
        private val log: Logger = Logger.getLogger(PhoneNumberChooseUser::class.java)
    }

    override fun action(context: AuthenticationFlowContext) = noop()

    override fun authenticate(context: AuthenticationFlowContext) {
        val event = context.event
        val authenticationSession = context.authenticationSession
        val phoneNumber = authenticationSession.getAuthNote(ATTEMPTED_PHONE_NUMBER)

        val realm = context.realm
        val attrName = context.phoneNumber()

        var user = context.user

        if (user != null && !user.isEnabled) {
            event.detail("phone_number", phoneNumber)
                .user(user)
                .error(Errors.USER_DISABLED)
            context.clearUser()
            context.resetFlow()
            return
        }

        if (user == null) {
            val userProvider = context.session.users()
            val users = userProvider
                .searchForUserByUserAttributeStream(realm, attrName, phoneNumber)
                .toList()

            if (users.size > 1) {
                log.warnf("Multiple users match %s=%s; using the first match", attrName, phoneNumber)
            }

            if (!users.isEmpty()) {
                user = users[0]
            }
        }

        if (user != null && user.isEnabled) {
            user.setAttribute(attrName, mutableListOf<String?>(phoneNumber))
            context.user = user
        } else {
            context.clearUser()
        }

        context
            .authenticationSession
            .setAuthNote(ATTEMPTED_PHONE_NUMBER, phoneNumber)

        context.success()
    }

}