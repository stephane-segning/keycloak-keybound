package com.ssegning.keycloak.keybound.authenticator

import com.ssegning.keycloak.keybound.core.AbstractAuthenticatorFactory
import com.ssegning.keycloak.keybound.spi.ApiGateway
import org.keycloak.models.KeycloakSession

class PhoneNumberValidateTanFactory : AbstractAuthenticatorFactory() {
    override fun create(session: KeycloakSession, apiGateway: ApiGateway) = PhoneNumberValidateTan(apiGateway)

    override fun getId() = ID

    override fun getDisplayType() = "SMS -5 Validate SMS Tan"

    override fun getHelpText() = "Validate the tan send in the previous page"

    companion object {
        const val ID = "phone-validate-tan"
    }
}