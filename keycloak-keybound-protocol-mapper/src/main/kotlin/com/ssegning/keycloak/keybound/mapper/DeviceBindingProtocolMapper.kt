package com.ssegning.keycloak.keybound.mapper

import org.keycloak.models.ClientSessionContext
import org.keycloak.models.KeycloakSession
import org.keycloak.models.ProtocolMapperModel
import org.keycloak.models.UserSessionModel
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper
import org.keycloak.representations.IDToken

class DeviceBindingProtocolMapper : AbstractOIDCProtocolMapper(), OIDCIDTokenMapper, OIDCAccessTokenMapper {

    companion object {
        const val PROVIDER_ID = "device-binding-mapper"
        const val DEVICE_ID_NOTE = "device.id"
        const val JKT_NOTE = "jkt"
    }

    override fun getDisplayCategory(): String {
        return TOKEN_MAPPER_CATEGORY
    }

    override fun getDisplayType(): String {
        return "Device Binding Mapper"
    }

    override fun getHelpText(): String {
        return "Maps device binding claims (cnf, device_id) to the token."
    }

    override fun getId(): String {
        return PROVIDER_ID
    }

    override fun setClaim(
        token: IDToken,
        mappingModel: ProtocolMapperModel,
        userSession: UserSessionModel,
        keycloakSession: KeycloakSession,
        clientSessionCtx: ClientSessionContext
    ) {
        val clientSession = clientSessionCtx.clientSession
        val deviceId = clientSession.getNote(DEVICE_ID_NOTE) ?: userSession.getNote(DEVICE_ID_NOTE)
        val jkt = clientSession.getNote(JKT_NOTE) ?: userSession.getNote(JKT_NOTE)

        if (deviceId != null) {
            token.otherClaims["device_id"] = deviceId
        }

        if (jkt != null) {
            val cnf = mapOf("jkt" to jkt)
            token.otherClaims["cnf"] = cnf
        }
    }
}
