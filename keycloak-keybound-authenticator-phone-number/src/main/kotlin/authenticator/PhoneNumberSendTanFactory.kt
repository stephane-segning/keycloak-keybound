package com.ssegning.keycloak.keybound.authenticator

import com.ssegning.keycloak.keybound.core.AbstractAuthenticatorFactory
import com.ssegning.keycloak.keybound.spi.ApiGateway
import org.keycloak.models.KeycloakSession

class PhoneNumberSendTanFactory : AbstractAuthenticatorFactory() {
    override fun create(session: KeycloakSession, apiGateway: ApiGateway) = PhoneNumberSendTan(apiGateway)

    override fun getId() = ID

    override fun getDisplayType() = "SMS -4 Send SMS Tan"

    override fun getHelpText() = "Send tan to a user before going to next page"

    companion object {
        const val ID = "phone-number-send-tan"
    }
}