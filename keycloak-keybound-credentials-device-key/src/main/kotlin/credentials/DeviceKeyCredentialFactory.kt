package com.ssegning.keycloak.keybound.credentials

import com.ssegning.keycloak.keybound.helper.getApi
import org.keycloak.models.KeycloakSession

class DeviceKeyCredentialFactory : AbstractCredentialProviderFactory<DeviceKeyCredential>() {
    override fun getId(): String = ID

    override fun create(session: KeycloakSession): DeviceKeyCredential = DeviceKeyCredential(session.getApi())

    companion object {
        const val ID = "device-key-credential-provider"
    }
}