package com.ssegning.keycloak.keybound.authenticator

import com.ssegning.keycloak.keybound.core.AbstractAuthenticatorFactory
import com.ssegning.keycloak.keybound.spi.ApiGateway
import org.keycloak.models.KeycloakSession

class PhoneNumberGetNumberFactory : AbstractAuthenticatorFactory() {
    override fun create(session: KeycloakSession, apiGateway: ApiGateway) = PhoneNumberGetNumber()

    override fun getId() = ID

    override fun getDisplayType() = "SMS -1 Get Phone number"

    override fun getHelpText() = "Get a user by his phone number"

    companion object {
        const val ID = "get-user-phone-number"
    }
}