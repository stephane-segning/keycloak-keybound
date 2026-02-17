package com.ssegning.keycloak.keybound.grants

import com.ssegning.keycloak.keybound.core.grants.AbstractGrantType
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.models.KeycloakSession

open class DeviceKeyGrantTypeFactory : AbstractGrantType<DeviceKeyGrantType>() {
    override fun getShortcut() = DEVICE_KEY_GRANT_SHORTCUT

    override fun create(
        session: KeycloakSession,
        apiGateway: ApiGateway,
    ) = DeviceKeyGrantType(apiGateway)

    override fun getId() = DEVICE_KEY_GRANT_ID

    companion object {
        const val DEVICE_KEY_GRANT_SHORTCUT = "dvk"
        const val DEVICE_KEY_GRANT_ID = "urn:ssegning:params:oauth:grant-type:device_key"
    }
}
