package com.ssegning.keycloak.keybound.endpoint

import org.keycloak.Config
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.services.resource.RealmResourceProvider
import org.keycloak.services.resource.RealmResourceProviderFactory

class DeviceApprovalResourceProviderFactory : RealmResourceProviderFactory {

    companion object {
        const val ID = "device-approval"
    }

    override fun create(session: KeycloakSession): RealmResourceProvider {
        return DeviceApprovalResourceProvider(session)
    }

    override fun init(config: Config.Scope) {
    }

    override fun postInit(factory: KeycloakSessionFactory) {
    }

    override fun close() {
    }

    override fun getId(): String {
        return ID
    }
}
