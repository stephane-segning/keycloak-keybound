package com.ssegning.keycloak.keybound.authenticator.enrollment

import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticator
import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticatorFactory
import org.keycloak.models.AuthenticationExecutionModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.provider.ProviderConfigProperty

class ConditionApprovalPathAuthenticatorFactory : ConditionalAuthenticatorFactory {
    override fun getId() = ID

    override fun getDisplayType() = "Condition - Approval Path"

    override fun getHelpText() =
        "Executes the current subflow only when enrollment routing selected the approval path."

    override fun getRequirementChoices() = REQUIREMENT_CHOICES

    override fun isUserSetupAllowed() = false

    override fun isConfigurable() = false

    override fun getConfigProperties(): MutableList<ProviderConfigProperty> = mutableListOf()

    override fun getSingleton(): ConditionalAuthenticator = ConditionApprovalPathAuthenticator.SINGLETON

    override fun init(config: org.keycloak.Config.Scope) {
        // No configuration needed
    }

    override fun postInit(factory: KeycloakSessionFactory) {
        // No post-init needed
    }

    override fun close() {
        // No resources to close
    }

    override fun create(session: KeycloakSession): ConditionalAuthenticator = getSingleton()

    companion object {
        const val ID = "condition-approval-path"
        private val REQUIREMENT_CHOICES = arrayOf(
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.DISABLED
        )
    }
}
