package com.ssegning.keycloak.keybound.core.authenticator

import com.ssegning.keycloak.keybound.core.helper.SPI_CORE_INFO
import com.ssegning.keycloak.keybound.core.helper.getApi
import com.ssegning.keycloak.keybound.core.helper.noop
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.Config
import org.keycloak.authentication.Authenticator
import org.keycloak.authentication.AuthenticatorFactory
import org.keycloak.models.AuthenticationExecutionModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.provider.ProviderConfigProperty
import org.keycloak.provider.ServerInfoAwareProviderFactory

abstract class AbstractAuthenticatorFactory : AuthenticatorFactory, ServerInfoAwareProviderFactory {
    override fun create(session: KeycloakSession) = create(session, session.getApi())

    abstract fun create(session: KeycloakSession, apiGateway: ApiGateway): Authenticator

    override fun init(config: Config.Scope) = noop()

    override fun postInit(factory: KeycloakSessionFactory) =
        noop()

    override fun close() = noop()

    override fun getReferenceCategory(): String = id

    override fun getOperationalInfo() = SPI_CORE_INFO

    override fun getRequirementChoices() = REQUIREMENT_CHOICES

    override fun getConfigProperties() = PROPERTIES

    override fun isConfigurable() = false

    override fun isUserSetupAllowed() = true

    companion object {
        val REQUIREMENT_CHOICES = arrayOf(
            AuthenticationExecutionModel.Requirement.REQUIRED
        )

        val PROPERTIES = listOf<ProviderConfigProperty>()
    }
}