package com.ssegning.keycloak.keybound.credentials

import com.ssegning.keycloak.keybound.spi.ApiGateway
import org.keycloak.models.KeycloakSession

class DeviceKeyCredentialFactory : AbstractCredentialProviderFactory<DeviceKeyCredential>() {
    override fun getId(): String = ID

    override fun create(session: KeycloakSession, apiGateway: ApiGateway): DeviceKeyCredential =
        DeviceKeyCredential(apiGateway)

    companion object {
        const val ID = "device-key-credential-provider"
    }
}
