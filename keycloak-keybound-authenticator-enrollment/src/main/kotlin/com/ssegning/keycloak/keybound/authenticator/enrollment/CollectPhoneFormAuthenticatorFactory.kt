package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticatorFactory
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.models.KeycloakSession

open class CollectPhoneFormAuthenticatorFactory : AbstractAuthenticatorFactory() {
    override fun create(session: KeycloakSession, apiGateway: ApiGateway) = CollectPhoneFormAuthenticator()

    override fun getId() = ID

    override fun getDisplayType() = "DK3- Collect Phone Form"

    override fun getHelpText() = "Displays a form to collect the user's phone number."

    companion object {
        const val ID = "collect-phone-form"
    }
}
