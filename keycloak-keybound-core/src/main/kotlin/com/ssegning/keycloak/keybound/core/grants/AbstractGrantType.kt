package com.ssegning.keycloak.keybound.core.grants

import com.ssegning.keycloak.keybound.core.helper.SPI_CORE_INFO
import com.ssegning.keycloak.keybound.core.helper.getApi
import com.ssegning.keycloak.keybound.core.helper.noop
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.Config
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.protocol.oidc.grants.OAuth2GrantTypeBase
import org.keycloak.protocol.oidc.grants.OAuth2GrantTypeFactory
import org.keycloak.provider.ServerInfoAwareProviderFactory

abstract class AbstractGrantType<T : OAuth2GrantTypeBase> :
    OAuth2GrantTypeFactory,
    ServerInfoAwareProviderFactory {
    abstract fun create(
        session: KeycloakSession,
        apiGateway: ApiGateway,
    ): T

    override fun create(session: KeycloakSession): T = create(session, session.getApi())

    override fun init(config: Config.Scope) = noop()

    override fun postInit(factory: KeycloakSessionFactory) = noop()

    override fun close() = noop()

    override fun getOperationalInfo(): Map<String, String> = SPI_CORE_INFO
}
