package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticatorFactory
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.authentication.Authenticator
import org.keycloak.models.AuthenticationExecutionModel
import org.keycloak.models.KeycloakSession

open class FindOrCreateUserAuthenticatorFactory : AbstractAuthenticatorFactory() {
    override fun create(
        session: KeycloakSession,
        apiGateway: ApiGateway,
    ): Authenticator = FindOrCreateUserAuthenticator()

    override fun getId() = ID

    override fun getDisplayType() = "DK9- Find or Create User"

    override fun getHelpText() = "Finds or creates a user using the verified phone number from the OTP step."

    override fun getRequirementChoices() =
        arrayOf(
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.ALTERNATIVE,
            AuthenticationExecutionModel.Requirement.DISABLED,
        )

    companion object {
        const val ID = "find-or-create-user"
    }
}
