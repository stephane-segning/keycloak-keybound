package com.ssegning.keycloak.keybound.enrollment

import com.ssegning.keycloak.keybound.authenticator.AbstractAuthenticatorFactory
import com.ssegning.keycloak.keybound.spi.ApiGateway
import org.keycloak.authentication.Authenticator
import org.keycloak.models.AuthenticationExecutionModel
import org.keycloak.models.KeycloakSession

open class PersistDeviceCredentialAuthenticatorFactory : AbstractAuthenticatorFactory() {
    override fun create(session: KeycloakSession, apiGateway: ApiGateway): Authenticator {
        return PersistDeviceCredentialAuthenticator()
    }

    override fun getId() = ID

    override fun getDisplayType() = "DK5- Persist Device Credential"

    override fun getHelpText() = "Persists the device credential (ID and Public Key) to the user's account."

    override fun getRequirementChoices() = arrayOf(
        AuthenticationExecutionModel.Requirement.REQUIRED,
        AuthenticationExecutionModel.Requirement.ALTERNATIVE,
        AuthenticationExecutionModel.Requirement.DISABLED
    )

    companion object {
        const val ID = "persist-device-credential"
    }
}
