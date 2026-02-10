package com.ssegning.keycloak.keybound.endpoint

import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.keycloak.models.TokenManager
import org.keycloak.representations.JsonWebToken
import org.slf4j.LoggerFactory

class DeviceApprovalResource(
    private val apiGateway: ApiGateway,
    private val tokenManager: TokenManager
) {

    companion object {
        private val log = LoggerFactory.getLogger(DeviceApprovalResource::class.java)
    }

    @GET
    @Path("status")
    @Produces(MediaType.APPLICATION_JSON)
    fun checkStatus(@QueryParam("token") token: String?): Response {
        if (token.isNullOrBlank()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(mapOf("error" to "Missing token"))
                .build()
        }

        val jwt = try {
            tokenManager.decode(token, JsonWebToken::class.java)
        } catch (e: Exception) {
            log.error(e.message, e)
            null
        }

        if (jwt == null || !jwt.isActive) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(mapOf("error" to "Invalid or expired token"))
                .build()
        }

        val requestId = jwt.otherClaims["request_id"] as? String

        if (requestId.isNullOrBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "Token missing request_id"))
                .build()
        }

        val status = apiGateway.checkApprovalStatus(requestId) ?: return Response.status(Response.Status.NOT_FOUND)
            .entity(mapOf("error" to "Approval request not found"))
            .build()

        return Response.ok(mapOf("status" to status.name.lowercase())).build()
    }
}
