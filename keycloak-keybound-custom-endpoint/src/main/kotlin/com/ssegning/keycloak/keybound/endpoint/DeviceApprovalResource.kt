package com.ssegning.keycloak.keybound.endpoint

import com.ssegning.keycloak.keybound.core.endpoint.AbstractResource
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import com.ssegning.keycloak.keybound.core.models.ApprovalPollingTokenClaims
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

        val claims = parseClaims(jwt)
        if (claims == null) {
            log.debug("Approval token missing required claims")
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(mapOf("error" to "Token missing context"))
                .build()
        }

        if (claims.realm != session.context.realm.name) {
            log.debug("Approval polling token realm mismatch")
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(mapOf("error" to "Invalid token realm"))
                .build()
        }

        if (claims.aud != APPROVAL_AUDIENCE) {
            log.debug("Approval token audience mismatch")
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(mapOf("error" to "Invalid token audience"))
                .build()
        }

        val nowSeconds = System.currentTimeMillis() / 1000
        if (claims.iat > nowSeconds || claims.nbf > nowSeconds) {
            log.debug("Approval token not yet valid")
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(mapOf("error" to "Token not yet valid"))
                .build()
        }

        val realm = session.context.realm
        val userSession = session.sessions().getUserSession(realm, claims.sid)
        if (userSession == null || userSession.user?.id != claims.sub) {
            log.debug("Approval token session or user mismatch")
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(mapOf("error" to "Invalid session"))
                .build()
        }

        val client = session.clients().getClientByClientId(realm, claims.client_id)
        val clientSession = client?.let { userSession.getAuthenticatedClientSessionByClient(it.clientId) }
        if (clientSession == null) {
            log.debug("Approval token client not bound to session")
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(mapOf("error" to "Invalid client"))
                .build()
        }

        val status = apiGateway.checkApprovalStatus(claims.request_id) ?: return Response.status(Response.Status.NOT_FOUND)
            .entity(mapOf("error" to "Approval request not found"))
            .build()

        log.debug("Approval status for request {} -> {}", claims.request_id, status)
        return Response.ok(mapOf("status" to status.name)).build()
    }

    private fun parseClaims(jwt: JsonWebToken): ApprovalPollingTokenClaims? {
        val realm = jwt.otherClaims["realm"] as? String ?: return null
        val clientId = jwt.otherClaims["client_id"] as? String ?: return null
        val audience = jwt.otherClaims["aud"] as? String ?: return null
        val sessionId = jwt.otherClaims["sid"] as? String ?: return null
        val subject = jwt.otherClaims["sub"] as? String ?: return null
        val issuedAt = jwt.iat ?: return null
        val notBefore = jwt.nbf ?: return null
        val requestId = jwt.otherClaims["request_id"] as? String ?: return null
        val tokenId = jwt.otherClaims["jti"] as? String ?: return null
        val exp = jwt.exp ?: return null
        return ApprovalPollingTokenClaims(
            realm = realm,
            client_id = clientId,
            aud = audience,
            sid = sessionId,
            sub = subject,
            request_id = requestId,
            iat = issuedAt,
            nbf = notBefore,
            jti = tokenId,
            exp = exp
        )
    }
}
