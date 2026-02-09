package com.ssegning.keycloak.keybound.grants

import com.ssegning.keycloak.keybound.spi.ApiGateway
import jakarta.ws.rs.core.Response
import org.keycloak.OAuthErrorException
import org.keycloak.events.Details
import org.keycloak.events.Errors
import org.keycloak.events.EventType
import org.keycloak.protocol.oidc.grants.OAuth2GrantType
import org.keycloak.protocol.oidc.grants.OAuth2GrantTypeBase
import org.keycloak.services.CorsErrorResponseException


class DeviceKeyGrantType(val apiGateway: ApiGateway) : OAuth2GrantTypeBase() {
    override fun getEventType(): EventType = EventType.REFRESH_TOKEN

    override fun process(context: OAuth2GrantType.Context): Response {
        val session = context.getSession()
        val client = context.getClient()

        if (client.isBearerOnly) {
            event.detail(Details.REASON, "Bearer-only client doesn't have device key")
            event.error(Errors.INVALID_CLIENT)

            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.UNAUTHORIZED_CLIENT,
                "Bearer-only client not allowed to retrieve service account",
                Response.Status.UNAUTHORIZED
            )
        }

        TODO("Not yet implemented")
    }
}