package com.ssegning.keycloak.keybound.endpoint

import com.ssegning.keycloak.keybound.core.endpoint.AbstractRealmResourceProviderFactory
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.models.KeycloakSession

open class DeviceApprovalResourceProviderFactory :
    AbstractRealmResourceProviderFactory<DeviceApprovalResourceProvider>() {

    companion object {
        const val ID = "device-approval"
    }

    override fun create(session: KeycloakSession, apiGateway: ApiGateway) =
        DeviceApprovalResourceProvider(session, apiGateway)

    override fun getId() = ID
}
