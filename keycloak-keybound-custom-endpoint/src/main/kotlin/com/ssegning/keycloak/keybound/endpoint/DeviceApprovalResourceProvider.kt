package com.ssegning.keycloak.keybound.endpoint

import org.keycloak.models.KeycloakSession
import org.keycloak.services.resource.RealmResourceProvider

class DeviceApprovalResourceProvider(private val session: KeycloakSession) : RealmResourceProvider {

    override fun getResource(): Any {
        return DeviceApprovalResource(session)
    }

    override fun close() {
    }
}
