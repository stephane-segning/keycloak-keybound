package com.ssegning.keycloak.keybound.api

import com.google.gson.Gson
import com.ssegning.keycloak.keybound.api.openapi.client.handler.*
import com.ssegning.keycloak.keybound.api.openapi.client.model.ApprovalCreateRequest
import com.ssegning.keycloak.keybound.api.openapi.client.model.ApprovalStatusResponse
import com.ssegning.keycloak.keybound.api.openapi.client.model.DeviceDescriptor
import com.ssegning.keycloak.keybound.helper.noop
import com.ssegning.keycloak.keybound.models.ApprovalStatus
import com.ssegning.keycloak.keybound.models.SmsRequest
import com.ssegning.keycloak.keybound.spi.ApiGateway
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.keycloak.authentication.AuthenticationFlowContext
import org.slf4j.LoggerFactory

open class Api(
    val devicesApi: DevicesApi,
    val approvalsApi: ApprovalsApi,
    val enrollmentApi: EnrollmentApi,
    private val baseUrl: String,
    private val client: OkHttpClient
) : ApiGateway {
    companion object {
        private val log = LoggerFactory.getLogger(Api::class.java)
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val gson = Gson()
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

        val url = "$baseUrl/v1/sms/send"
        val jsonBody = mapOf("phoneNumber" to phoneNumber, "otp" to otp)
        val body = gson.toJson(jsonBody).toRequestBody(JSON)

        val httpRequest = Request.Builder()
            .url(url)
            .post(body)
            .build()

        return client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                log.error("Failed to send SMS: ${response.code} ${response.message}")
                throw RuntimeException("Failed to send SMS: ${response.code}")
            }

            val responseBody = response.body?.string()
                ?: throw RuntimeException("Empty response from SMS backend")

            val jsonResponse = gson.fromJson(responseBody, Map::class.java)
            jsonResponse["hash"] as? String ?: throw RuntimeException("Hash not found in response")
        }
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
        val url = "$baseUrl/v1/sms/confirm"
        val jsonBody = mapOf("hash" to hash, "otp" to code)
        val body = gson.toJson(jsonBody).toRequestBody(JSON)

        val httpRequest = Request.Builder()
            .url(url)
            .post(body)
            .build()

        return try {
            client.newCall(httpRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    // Assuming the backend returns a boolean or a string "true"/"false"
                    if (responseBody?.trim().equals("true", ignoreCase = true)) "true" else "false"
                } else {
                    log.warn("SMS confirmation failed: ${response.code} ${response.message}")
                    "false"
                }
            }
        } catch (e: Exception) {
            log.error("Error confirming SMS code", e)
            "false"
        }
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
