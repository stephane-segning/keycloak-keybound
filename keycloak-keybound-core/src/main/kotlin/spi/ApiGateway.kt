package com.ssegning.keycloak.keybound.spi

import com.ssegning.keycloak.keybound.models.SmsRequest
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
}