package com.ssegning.keycloak.keybound.endpoint

import com.ssegning.keycloak.keybound.spi.ApiGateway
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.keycloak.models.KeycloakSession

class DeviceApprovalResource(private val session: KeycloakSession) {

    @GET
    @Path("status")
    @Produces(MediaType.APPLICATION_JSON)
    fun checkStatus(@QueryParam("request_id") requestId: String?): Response {
        if (requestId.isNullOrBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "Missing request_id"))
                .build()
        }

        val apiGateway = session.getProvider(ApiGateway::class.java)
        if (apiGateway == null) {
             return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "ApiGateway provider not found"))
                .build()
        }

        val status = apiGateway.checkApprovalStatus(requestId)

        if (status == null) {
             return Response.status(Response.Status.NOT_FOUND)
                .entity(mapOf("error" to "Approval request not found"))
                .build()
        }

        return Response.ok(mapOf("status" to status.name.lowercase())).build()
    }
}
