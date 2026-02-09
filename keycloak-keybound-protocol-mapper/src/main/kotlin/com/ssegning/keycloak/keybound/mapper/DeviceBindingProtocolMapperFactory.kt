package com.ssegning.keycloak.keybound.mapper

import org.keycloak.models.KeycloakSession
import org.keycloak.models.ProtocolMapperModel
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapperFactory
import org.keycloak.provider.ProviderConfigProperty

class DeviceBindingProtocolMapperFactory : AbstractOIDCProtocolMapperFactory() {

    override fun create(session: KeycloakSession): DeviceBindingProtocolMapper {
        return DeviceBindingProtocolMapper()
    }

    override fun getId(): String {
        return DeviceBindingProtocolMapper.PROVIDER_ID
    }

    override fun getDisplayType(): String {
        return "Device Binding Mapper"
    }

    override fun getHelpText(): String {
        return "Maps device binding claims (cnf, device_id) to the token."
    }

    override fun getConfigProperties(): List<ProviderConfigProperty> {
        return emptyList()
    }
}
