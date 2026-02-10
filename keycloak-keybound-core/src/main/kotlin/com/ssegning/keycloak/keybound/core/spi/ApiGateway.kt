package com.ssegning.keycloak.keybound.core.spi

import com.ssegning.keycloak.keybound.core.models.ApprovalStatus
import com.ssegning.keycloak.keybound.core.models.DeviceDescriptor
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
}