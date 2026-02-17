package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticatorFactory
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.models.AuthenticationExecutionModel
import org.keycloak.models.KeycloakSession

open class WaitForApprovalFormAuthenticatorFactory : AbstractAuthenticatorFactory() {
    override fun getId() = ID

    override fun getDisplayType() = "DK7- Wait For Approval"

    override fun getHelpText() = "Displays approval wait screen and polls approval status."

    override fun create(
        session: KeycloakSession,
        apiGateway: ApiGateway,
    ) = WaitForApprovalFormAuthenticator(apiGateway)

    override fun getRequirementChoices() = REQUIREMENT_CHOICES

    companion object {
        const val ID = "keybound-wait-approval"

        val REQUIREMENT_CHOICES =
            arrayOf(
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.ALTERNATIVE,
                AuthenticationExecutionModel.Requirement.CONDITIONAL,
                AuthenticationExecutionModel.Requirement.DISABLED,
            )
    }
}
