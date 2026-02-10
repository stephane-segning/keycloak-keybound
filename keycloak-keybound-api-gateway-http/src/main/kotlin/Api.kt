package com.ssegning.keycloak.keybound.api

import com.ssegning.keycloak.keybound.api.openapi.client.handler.ApprovalsApi
import com.ssegning.keycloak.keybound.api.openapi.client.handler.DevicesApi
import com.ssegning.keycloak.keybound.api.openapi.client.handler.EnrollmentApi
import com.ssegning.keycloak.keybound.api.openapi.client.model.ApprovalCreateRequest
import com.ssegning.keycloak.keybound.api.openapi.client.model.ApprovalStatusResponse
import com.ssegning.keycloak.keybound.api.openapi.client.model.DeviceDescriptor
import com.ssegning.keycloak.keybound.api.openapi.client.model.SmsConfirmRequest
import com.ssegning.keycloak.keybound.api.openapi.client.model.SmsSendRequest
import com.ssegning.keycloak.keybound.core.helper.noop
import com.ssegning.keycloak.keybound.core.models.ApprovalStatus
import com.ssegning.keycloak.keybound.core.models.SmsRequest
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
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

    /**
     * Sends an SMS with an OTP to the given phone number.
     *
     * This method makes a POST request to the backend endpoint `/v1/sms/send`.
     * The request body contains the phone number and the OTP.
     * The backend is expected to return a JSON object containing a hash, which is returned by this method.
     */
    override fun sendSmsAndGetHash(
        context: AuthenticationFlowContext,
        request: SmsRequest,
        phoneNumber: String
    ): String {
        val otp = request.metadata?.get("otp") as? String
            ?: throw IllegalArgumentException("OTP not found in request metadata")

        val smsSendRequest = SmsSendRequest(
            realm = request.realm ?: context.realm?.name.orEmpty(),
            clientId = request.clientId ?: context.authenticationSession.client.clientId,
            phoneNumber = phoneNumber,
            otp = otp,
            userId = context.user?.id,
            sessionId = request.sessionId,
            traceId = request.traceId,
            metadata = request.metadata?.mapNotNull { (key, value) ->
                key?.let { nonNullKey ->
                    value?.let { nonNullValue -> nonNullKey to nonNullValue }
                }
            }?.toMap()
        )

        return enrollmentApi.sendSms(smsSendRequest).hash
    }

    /**
     * Confirms the SMS code (OTP) using the hash received earlier.
     *
     * This method makes a POST request to the backend endpoint `/v1/sms/confirm`.
     * The request body contains the hash and the OTP.
     * The backend is expected to return `true` if the code is valid, and `false` otherwise.
     */
    override fun confirmSmsCode(
        context: AuthenticationFlowContext,
        request: SmsRequest,
        phoneNumber: String,
        code: String,
        hash: String
    ): String {
        val smsConfirmRequest = SmsConfirmRequest(hash = hash, otp = code)
        return enrollmentApi.confirmSms(smsConfirmRequest).confirmed.toString()
    }

    override fun checkApprovalStatus(requestId: String): ApprovalStatus? = try {
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

    override fun createApprovalRequest(
        context: AuthenticationFlowContext,
        userId: String,
        deviceData: com.ssegning.keycloak.keybound.core.models.DeviceDescriptor
    ): String? = try {
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

    override fun close() = noop()
}
