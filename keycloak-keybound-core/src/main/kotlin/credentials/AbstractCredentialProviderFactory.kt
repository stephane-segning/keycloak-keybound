package com.ssegning.keycloak.keybound.credentials

import com.ssegning.keycloak.keybound.helper.noop
import org.keycloak.Config
import org.keycloak.credential.CredentialProvider
import org.keycloak.credential.CredentialProviderFactory
import org.keycloak.models.KeycloakSessionFactory

abstract class AbstractCredentialProviderFactory<T: CredentialProvider<*>>: CredentialProviderFactory<T> {
    override fun init(config: Config.Scope) = noop()

    override fun postInit(factory: KeycloakSessionFactory) = noop()

    override fun close() = noop()
}