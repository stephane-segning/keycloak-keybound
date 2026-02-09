package com.ssegning.keycloak.keybound.grants

import com.ssegning.keycloak.keybound.helper.getApi
import com.ssegning.keycloak.keybound.helper.noop
import org.keycloak.Config
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.protocol.oidc.grants.OAuth2GrantTypeFactory

class DeviceKeyGrantTypeFactory : OAuth2GrantTypeFactory {
    override fun getShortcut() = DEVICE_KEY_GRANT_SHORTCUT

    override fun create(session: KeycloakSession) = DeviceKeyGrantType(session.getApi())

    override fun init(config: Config.Scope) = noop()

    override fun postInit(factory: KeycloakSessionFactory) = noop()

    override fun close() = noop()

    override fun getId() = DEVICE_KEY_GRANT_ID

    companion object {
        const val DEVICE_KEY_GRANT_SHORTCUT = "dvk"
        const val DEVICE_KEY_GRANT_ID = "device_key_grant_id"
    }
}