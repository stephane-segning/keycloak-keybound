package com.ssegning.keycloak.keybound.credentials

import com.ssegning.keycloak.keybound.core.credentials.AbstractCredentialProviderFactory
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.models.KeycloakSession

open class DeviceKeyCredentialFactory : AbstractCredentialProviderFactory<DeviceKeyCredential>() {
    override fun getId(): String = ID

    override fun create(session: KeycloakSession, apiGateway: ApiGateway): DeviceKeyCredential =
        DeviceKeyCredential(session, apiGateway)

    companion object {
        const val ID = "device-key-credential-provider"
    }
}
