package com.ssegning.keycloak.keybound.approval

import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticatorFactory
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.models.AuthenticationExecutionModel
import org.keycloak.models.KeycloakSession

open class WaitForApprovalFormAuthenticatorFactory : AbstractAuthenticatorFactory() {
    override fun getId() = "keybound-wait-approval"

    override fun getDisplayType() = "AP2- Wait For Approval"

    override fun getHelpText() = "Displays a waiting page and polls for approval status."

    override fun create(session: KeycloakSession, apiGateway: ApiGateway) =
        WaitForApprovalFormAuthenticator(apiGateway)

    override fun getRequirementChoices() = REQUIREMENT_CHOICES

    companion object {
        val REQUIREMENT_CHOICES = arrayOf(
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.ALTERNATIVE,
            AuthenticationExecutionModel.Requirement.CONDITIONAL,
            AuthenticationExecutionModel.Requirement.DISABLED,
        )
    }
}