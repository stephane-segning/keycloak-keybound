package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticatorFactory
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.models.AuthenticationExecutionModel
import org.keycloak.models.KeycloakSession

open class StartApprovalRequestAuthenticatorFactory : AbstractAuthenticatorFactory() {
    override fun getId() = ID

    override fun getDisplayType() = "DK6- Start Approval Request"

    override fun getHelpText() = "Initiates the backend approval request for existing user devices."

    override fun create(session: KeycloakSession, apiGateway: ApiGateway) =
        StartApprovalRequestAuthenticator(apiGateway)

    override fun getRequirementChoices() = REQUIREMENT_CHOICES

    companion object {
        const val ID = "keybound-start-approval-request"

        val REQUIREMENT_CHOICES = arrayOf(
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.ALTERNATIVE,
            AuthenticationExecutionModel.Requirement.CONDITIONAL,
            AuthenticationExecutionModel.Requirement.DISABLED,
        )
    }
}
