package com.ssegning.keycloak.keybound.credentials

import com.ssegning.keycloak.keybound.helper.getApi
import com.ssegning.keycloak.keybound.helper.noop
import com.ssegning.keycloak.keybound.spi.ApiGateway
import org.keycloak.Config
import org.keycloak.credential.CredentialProvider
import org.keycloak.credential.CredentialProviderFactory
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory

abstract class AbstractCredentialProviderFactory<T : CredentialProvider<*>> : CredentialProviderFactory<T> {
    abstract fun create(session: KeycloakSession, apiGateway: ApiGateway): T

    override fun create(session: KeycloakSession): T = create(session, session.getApi())

    override fun init(config: Config.Scope) = noop()

    override fun postInit(factory: KeycloakSessionFactory) = noop()

    override fun close() = noop()
}