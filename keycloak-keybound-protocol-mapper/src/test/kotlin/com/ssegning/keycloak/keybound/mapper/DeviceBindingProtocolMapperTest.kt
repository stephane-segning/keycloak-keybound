package com.ssegning.keycloak.keybound.mapper

import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import org.keycloak.models.ClientSessionContext
import org.keycloak.models.ClientSessionModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.ProtocolMapperModel
import org.keycloak.models.UserSessionModel
import org.keycloak.representations.IDToken

class DeviceBindingProtocolMapperTest {
    @Test
    fun `maps device_id and cnf jkt claims from session notes`() {
        val mapper = DeviceBindingProtocolMapper()
        val token = IDToken()
        val mappingModel = mockk<ProtocolMapperModel>(relaxed = true)
        val keycloakSession = mockk<KeycloakSession>(relaxed = true)
        val clientSession = mockk<ClientSessionModel>()
        val userSession = mockk<UserSessionModel>()
        val clientSessionCtx = mockk<ClientSessionContext>()

        every { clientSessionCtx.clientSession } returns clientSession
        every { clientSession.getNote(DeviceBindingProtocolMapper.DEVICE_ID_NOTE) } returns "device-123"
        every { userSession.getNote(DeviceBindingProtocolMapper.DEVICE_ID_NOTE) } returns null
        every { clientSession.getNote(DeviceBindingProtocolMapper.JKT_NOTE) } returns "jkt-abc"
        every { userSession.getNote(DeviceBindingProtocolMapper.JKT_NOTE) } returns null

        mapper.setClaim(token, mappingModel, userSession, keycloakSession, clientSessionCtx)

        assertEquals("device-123", token.otherClaims["device_id"])
        val cnf = token.otherClaims["cnf"] as Map<*, *>
        assertEquals("jkt-abc", cnf["jkt"])
    }
}
