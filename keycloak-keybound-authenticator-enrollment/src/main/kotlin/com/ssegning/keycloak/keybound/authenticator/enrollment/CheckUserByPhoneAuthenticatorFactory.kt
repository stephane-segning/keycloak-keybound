package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticatorFactory
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.models.KeycloakSession

open class CheckUserByPhoneAuthenticatorFactory : AbstractAuthenticatorFactory() {
    override fun create(
        session: KeycloakSession,
        apiGateway: ApiGateway,
    ) = CheckUserByPhoneAuthenticator()

    override fun getId() = ID

    override fun getDisplayType() = "DK4- Check User by Phone Number"

    override fun getHelpText() = "Finds an existing user by the collected E.164 phone without creating a new user."

    companion object {
        const val ID = "check-user-by-phone"
    }
}
