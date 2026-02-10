package com.ssegning.keycloak.keybound.endpoint

import com.ssegning.keycloak.keybound.core.helper.noop
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.models.KeycloakSession
import org.keycloak.services.resource.RealmResourceProvider

class DeviceApprovalResourceProvider(
    private val session: KeycloakSession,
    private val apiGateway: ApiGateway
) : RealmResourceProvider {
    override fun getResource(): Any = DeviceApprovalResource(apiGateway, session.tokens())

    override fun close() = noop()
}
