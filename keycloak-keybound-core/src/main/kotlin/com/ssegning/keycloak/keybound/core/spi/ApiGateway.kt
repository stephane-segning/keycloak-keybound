package com.ssegning.keycloak.keybound.core.spi

import com.ssegning.keycloak.keybound.core.models.ApprovalStatus
import com.ssegning.keycloak.keybound.core.models.DeviceLookupResult
import com.ssegning.keycloak.keybound.core.models.DeviceRecord
import com.ssegning.keycloak.keybound.core.models.DeviceDescriptor
import com.ssegning.keycloak.keybound.core.models.EnrollmentPrecheckResult
import com.ssegning.keycloak.keybound.core.models.SmsRequest
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.provider.Provider

interface ApiGateway : Provider {
    fun sendSmsAndGetHash(context: AuthenticationFlowContext, request: SmsRequest, phoneNumber: String): String?

    fun confirmSmsCode(
        context: AuthenticationFlowContext,
        request: SmsRequest,
        phoneNumber: String,
        code: String,
        hash: String
    ): String?

    fun checkApprovalStatus(requestId: String): ApprovalStatus?

    fun createApprovalRequest(
        context: AuthenticationFlowContext,
        userId: String,
        deviceData: DeviceDescriptor
    ): String?

    fun enrollmentPrecheck(
        context: AuthenticationFlowContext,
        userId: String,
        userHint: String?,
        deviceData: DeviceDescriptor
    ): EnrollmentPrecheckResult?

    fun enrollmentBind(
        context: AuthenticationFlowContext,
        userId: String,
        userHint: String?,
        deviceData: DeviceDescriptor,
        attributes: Map<String, String>? = null,
        proof: Map<String, Any>? = null
    ): Boolean

    fun listUserDevices(userId: String, includeDisabled: Boolean = true): List<DeviceRecord>?

    fun lookupDevice(deviceId: String? = null, jkt: String? = null): DeviceLookupResult?

    fun disableDevice(userId: String, deviceId: String): Boolean
}
