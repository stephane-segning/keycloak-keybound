package com.ssegning.keycloak.keybound.api

import com.ssegning.keycloak.keybound.api.openapi.client.handler.*
import com.ssegning.keycloak.keybound.api.openapi.client.model.ApprovalCreateRequest
import com.ssegning.keycloak.keybound.api.openapi.client.model.ApprovalStatusResponse
import com.ssegning.keycloak.keybound.api.openapi.client.model.DeviceDescriptor
import com.ssegning.keycloak.keybound.helper.noop
import com.ssegning.keycloak.keybound.models.ApprovalStatus
import com.ssegning.keycloak.keybound.models.SmsRequest
import com.ssegning.keycloak.keybound.spi.ApiGateway
import org.keycloak.authentication.AuthenticationFlowContext
import org.slf4j.LoggerFactory

open class Api(
    val devicesApi: DevicesApi,
    val approvalsApi: ApprovalsApi,
    val enrollmentApi: EnrollmentApi
) : ApiGateway {
    companion object {
        private val log = LoggerFactory.getLogger(Api::class.java)
    }

    override fun sendSmsAndGetHash(
        context: AuthenticationFlowContext,
        request: SmsRequest,
        phoneNumber: String
    ): String {
        TODO("Not yet implemented")
    }

    override fun confirmSmsCode(
        context: AuthenticationFlowContext,
        request: SmsRequest,
        phoneNumber: String,
        code: String,
        hash: String
    ): String {
        TODO("Not yet implemented")
    }

    override fun checkApprovalStatus(requestId: String): ApprovalStatus? {
        return try {
            val response = approvalsApi.getApproval(requestId)
            when (response.status) {
                ApprovalStatusResponse.Status.PENDING -> ApprovalStatus.PENDING
                ApprovalStatusResponse.Status.APPROVED -> ApprovalStatus.APPROVED
                ApprovalStatusResponse.Status.DENIED -> ApprovalStatus.DENIED
                ApprovalStatusResponse.Status.EXPIRED -> ApprovalStatus.EXPIRED
            }
        } catch (e: Exception) {
            log.error("Failed to check approval status for request $requestId", e)
            null
        }
    }

    override fun createApprovalRequest(
        context: AuthenticationFlowContext,
        userId: String,
        deviceData: com.ssegning.keycloak.keybound.models.DeviceDescriptor
    ): String? {
        return try {
            val response = approvalsApi.createApproval(
                ApprovalCreateRequest(
                    realm = context.realm.name,
                    clientId = context.authenticationSession.client.clientId,
                    userId = userId,
                    newDevice = DeviceDescriptor(
                        deviceId = deviceData.deviceId,
                        jkt = deviceData.jkt,
                        publicJwk = deviceData.publicJwk,
                        platform = deviceData.platform,
                        model = deviceData.model,
                        appVersion = deviceData.appVersion
                    )
                )
            )
            response.requestId
        } catch (e: Exception) {
            log.error("Failed to create approval request for user $userId", e)
            null
        }
    }

    override fun close() = noop()
}
