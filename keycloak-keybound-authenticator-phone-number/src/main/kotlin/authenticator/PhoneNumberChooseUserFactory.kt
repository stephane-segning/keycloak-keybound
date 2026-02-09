package com.ssegning.keycloak.keybound.authenticator

import com.ssegning.keycloak.keybound.core.AbstractAuthenticatorFactory
import com.ssegning.keycloak.keybound.spi.ApiGateway
import org.keycloak.models.KeycloakSession

class PhoneNumberChooseUserFactory : AbstractAuthenticatorFactory() {
    override fun create(session: KeycloakSession, apiGateway: ApiGateway) = PhoneNumberChooseUser()

    override fun getId() = ID

    override fun getDisplayType() = "SMS -3 Choose user by Phone number"

    override fun getHelpText() = "Resolve a user by phone number (without creating a user before phone verification)"

    companion object {
        const val ID: String = "phone-number-choose-user"
    }
}