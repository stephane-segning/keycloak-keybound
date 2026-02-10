package com.ssegning.keycloak.keybound.core.endpoint

import com.ssegning.keycloak.keybound.core.helper.SPI_CORE_INFO
import com.ssegning.keycloak.keybound.core.helper.getApi
import com.ssegning.keycloak.keybound.core.helper.noop
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.Config
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.provider.ServerInfoAwareProviderFactory
import org.keycloak.services.resource.RealmResourceProvider
import org.keycloak.services.resource.RealmResourceProviderFactory

abstract class AbstractRealmResourceProviderFactory<T : RealmResourceProvider> :
    RealmResourceProviderFactory, ServerInfoAwareProviderFactory {
    override fun init(config: Config.Scope) = noop()

    override fun postInit(factory: KeycloakSessionFactory) = noop()

    override fun close() = noop()

    override fun create(session: KeycloakSession) = create(session, session.getApi())

    abstract fun create(session: KeycloakSession, apiGateway: ApiGateway): T

    override fun getOperationalInfo(): Map<String, String> = SPI_CORE_INFO
}