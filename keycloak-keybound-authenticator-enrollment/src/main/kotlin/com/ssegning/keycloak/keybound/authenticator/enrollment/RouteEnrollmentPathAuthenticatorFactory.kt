package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticatorFactory
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.models.KeycloakSession

open class RouteEnrollmentPathAuthenticatorFactory : AbstractAuthenticatorFactory() {
    override fun create(
        session: KeycloakSession,
        apiGateway: ApiGateway,
    ) = RouteEnrollmentPathAuthenticator()

    override fun getId() = ID

    override fun getDisplayType() = "DK5- Differentiate Approval vs OTP Path"

    override fun getHelpText() = "Selects approval path when a resolved user already has device credentials, otherwise selects OTP path."

    companion object {
        const val ID = "route-enrollment-path"
    }
}
