package com.ssegning.keycloak.keybound.endpoint

import com.ssegning.keycloak.keybound.helper.getApi
import org.keycloak.models.KeycloakSession
import org.keycloak.services.resource.RealmResourceProvider

class DeviceApprovalResourceProvider(
    private val session: KeycloakSession
) : RealmResourceProvider {

    override fun getResource(): Any = DeviceApprovalResource(session.getApi(), session.tokens())

    override fun close() {
    }
}
