package com.ssegning.keycloak.keybound.endpoint

import com.ssegning.keycloak.keybound.core.endpoint.AbstractResource
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.keycloak.models.KeycloakSession
import org.keycloak.models.TokenManager
import org.keycloak.representations.JsonWebToken
import org.slf4j.LoggerFactory

class DeviceApprovalResource(
    private val session: KeycloakSession,
    apiGateway: ApiGateway,
    private val tokenManager: TokenManager
) : AbstractResource(apiGateway) {

    companion object {
        private val log = LoggerFactory.getLogger(DeviceApprovalResource::class.java)
        private const val APPROVAL_AUDIENCE = "device-approval-status"
    }

    @GET
    @Path("status")
    @Produces(MediaType.APPLICATION_JSON)
    fun checkStatus(@QueryParam("token") token: String?): Response {
        if (token.isNullOrBlank()) {
            log.debug("Missing token for approval status check")
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(mapOf("error" to "Missing token"))
                .build()
        }

        val jwt = try {
            tokenManager.decode(token, JsonWebToken::class.java)
        } catch (e: Exception) {
            log.error("Failed to decode token for approval status", e)
            null
        }

        if (jwt == null || !jwt.isActive) {
            log.debug("Approval polling token invalid or expired")
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(mapOf("error" to "Invalid or expired token"))
                .build()
        }

        val realmName = jwt.otherClaims["realm"] as? String
        if (realmName.isNullOrBlank() || realmName != session.context.realm.name) {
            log.debug("Approval polling token realm mismatch")
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(mapOf("error" to "Invalid token realm"))
                .build()
        }

        val clientId = jwt.otherClaims["client_id"] as? String
        val audience = jwt.otherClaims["aud"] as? String
        val sessionId = jwt.otherClaims["sid"] as? String
        val subject = jwt.otherClaims["sub"] as? String
        val issuedAt = jwt.otherClaims["iat"] as? Number
        val notBefore = jwt.otherClaims["nbf"] as? Number

        if (clientId.isNullOrBlank() || sessionId.isNullOrBlank() || subject.isNullOrBlank()) {
            log.debug("Approval token missing client/session/user context")
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(mapOf("error" to "Token missing context"))
                .build()
        }

        if (audience != APPROVAL_AUDIENCE) {
            log.debug("Approval token audience mismatch")
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(mapOf("error" to "Invalid token audience"))
                .build()
        }

        val nowSeconds = System.currentTimeMillis() / 1000
        if ((issuedAt?.toLong() ?: 0L) > nowSeconds || (notBefore?.toLong() ?: 0L) > nowSeconds) {
            log.debug("Approval token not yet valid")
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(mapOf("error" to "Token not yet valid"))
                .build()
        }

        val realm = session.context.realm
        val userSession = session.sessions().getUserSession(realm, sessionId)
        if (userSession == null || userSession.user?.id != subject) {
            log.debug("Approval token session or user mismatch")
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(mapOf("error" to "Invalid session"))
                .build()
        }

        val client = session.clients().getClientByClientId(realm, clientId)
        val clientSession = client?.let { userSession.getAuthenticatedClientSessionByClient(it) }
        if (clientSession == null) {
            log.debug("Approval token client not bound to session")
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(mapOf("error" to "Invalid client"))
                .build()
        }

        val requestId = jwt.otherClaims["request_id"] as? String

        if (requestId.isNullOrBlank()) {
            log.debug("Approval token missing request_id claim")
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "Token missing request_id"))
                .build()
        }

        val status = apiGateway.checkApprovalStatus(requestId) ?: return Response.status(Response.Status.NOT_FOUND)
            .entity(mapOf("error" to "Approval request not found"))
            .build()

        log.debug("Approval status for request {} -> {}", requestId, status)
        return Response.ok(mapOf("status" to status.name)).build()
    }
}
