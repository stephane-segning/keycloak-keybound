package com.ssegning.keycloak.keybound.grants

import com.ssegning.keycloak.keybound.helper.getApi
import com.ssegning.keycloak.keybound.helper.noop
import com.ssegning.keycloak.keybound.spi.ApiGateway
import org.keycloak.Config
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.protocol.oidc.grants.OAuth2GrantTypeBase
import org.keycloak.protocol.oidc.grants.OAuth2GrantTypeFactory

abstract class AbstractGrantType<T : OAuth2GrantTypeBase> : OAuth2GrantTypeFactory {
    abstract fun create(session: KeycloakSession, apiGateway: ApiGateway): T

    override fun create(session: KeycloakSession): T = create(session, session.getApi())

    override fun init(config: Config.Scope) = noop()

    override fun postInit(factory: KeycloakSessionFactory) =
        noop()

    override fun close() = noop()
}