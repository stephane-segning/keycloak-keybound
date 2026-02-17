package com.ssegning.keycloak.keybound.endpoint

import com.ssegning.keycloak.keybound.core.endpoint.AbstractRealmResourceProviderFactory
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.models.KeycloakSession

open class PublicKeyLoginResourceProviderFactory : AbstractRealmResourceProviderFactory<PublicKeyLoginResourceProvider>() {
    companion object {
        const val ID = "device-public-key-login"
    }

    override fun create(
        session: KeycloakSession,
        apiGateway: ApiGateway,
    ): PublicKeyLoginResourceProvider = PublicKeyLoginResourceProvider(session, apiGateway)

    override fun getId(): String = ID
}
