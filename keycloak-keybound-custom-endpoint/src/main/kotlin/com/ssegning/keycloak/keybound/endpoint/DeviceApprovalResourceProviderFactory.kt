package com.ssegning.keycloak.keybound.endpoint

import com.ssegning.keycloak.keybound.core.helper.noop
import org.keycloak.Config
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.services.resource.RealmResourceProviderFactory

open class DeviceApprovalResourceProviderFactory : RealmResourceProviderFactory {
    companion object {
        const val ID = "device-approval"
    }

    override fun create(session: KeycloakSession) = DeviceApprovalResourceProvider(session)

    override fun init(config: Config.Scope) = noop()

    override fun postInit(factory: KeycloakSessionFactory) = noop()

    override fun close() = noop()

    override fun getId() = ID
}
