package com.ssegning.keycloak.keybound.mapper

import org.keycloak.models.ClientSessionContext
import org.keycloak.models.KeycloakSession
import org.keycloak.models.ProtocolMapperModel
import org.keycloak.models.UserSessionModel
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper
import org.keycloak.provider.ProviderConfigProperty
import org.keycloak.representations.IDToken
import org.slf4j.LoggerFactory

open class DeviceBindingProtocolMapper : AbstractOIDCProtocolMapper(), OIDCIDTokenMapper, OIDCAccessTokenMapper {

    companion object {
        const val PROVIDER_ID = "device-binding-mapper"
        const val DEVICE_ID_NOTE = "device.id"
        const val JKT_NOTE = "jkt"
        const val CNF_JKT_NOTE = "cnf.jkt"
    }

    private val log = LoggerFactory.getLogger(DeviceBindingProtocolMapper::class.java)

    override fun getDisplayCategory() = TOKEN_MAPPER_CATEGORY

    override fun getDisplayType() = "Device Binding Mapper"

    override fun getHelpText() = "Maps device binding claims (cnf, device_id) to the token."

    override fun getConfigProperties(): List<ProviderConfigProperty> = emptyList()

    override fun getId() = PROVIDER_ID

    override fun setClaim(
        token: IDToken,
        mappingModel: ProtocolMapperModel,
        userSession: UserSessionModel,
        keycloakSession: KeycloakSession,
        clientSessionCtx: ClientSessionContext
    ) {
        val clientSession = clientSessionCtx.clientSession
        val deviceId = clientSession.getNote(DEVICE_ID_NOTE) ?: userSession.getNote(DEVICE_ID_NOTE)
        val jkt = clientSession.getNote(JKT_NOTE)
            ?: userSession.getNote(JKT_NOTE)
            ?: clientSession.getNote(CNF_JKT_NOTE)
            ?: userSession.getNote(CNF_JKT_NOTE)

        if (deviceId == null && jkt == null) {
            log.debug("No device binding notes found for clientSession {} userSession {}", clientSession.id, userSession.id)
        }

        if (deviceId != null) {
            token.otherClaims["device_id"] = deviceId
            log.debug("Mapped device_id claim {}", deviceId)
        }

        if (jkt != null) {
            val cnf = mapOf("jkt" to jkt)
            token.otherClaims["cnf"] = cnf
            log.debug("Mapped cnf.jkt claim {}", jkt)
        }
    }
}
