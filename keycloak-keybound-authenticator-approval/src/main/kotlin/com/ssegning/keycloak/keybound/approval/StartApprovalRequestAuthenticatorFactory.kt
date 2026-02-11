package com.ssegning.keycloak.keybound.approval

import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticatorFactory
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.models.AuthenticationExecutionModel
import org.keycloak.models.KeycloakSession

open class StartApprovalRequestAuthenticatorFactory : AbstractAuthenticatorFactory() {
    override fun getId() = "keybound-start-approval-request"

    override fun getDisplayType() = "AP1- Start Approval Request"

    override fun getHelpText() = "Initiates a device approval request with the backend."

    override fun create(session: KeycloakSession, apiGateway: ApiGateway) =
        StartApprovalRequestAuthenticator(apiGateway)

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