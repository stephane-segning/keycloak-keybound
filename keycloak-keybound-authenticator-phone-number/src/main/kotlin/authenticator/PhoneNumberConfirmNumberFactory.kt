package com.ssegning.keycloak.keybound.authenticator

import com.ssegning.keycloak.keybound.core.AbstractAuthenticatorFactory
import com.ssegning.keycloak.keybound.spi.ApiGateway
import org.keycloak.models.KeycloakSession

class PhoneNumberConfirmNumberFactory : AbstractAuthenticatorFactory() {
    override fun create(session: KeycloakSession, apiGateway: ApiGateway) = PhoneNumberConfirmNumber()

    override fun getId() = ID

    override fun getDisplayType() = "SMS -2 Confirm phone number"

    override fun getHelpText() = "Confirm user's phone number before sending"

    companion object {
        const val ID = "phone-number-confirm-number"
    }
}