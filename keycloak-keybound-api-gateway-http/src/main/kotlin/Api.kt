package com.ssegning.keycloak.keybound.api

import com.ssegning.keycloak.keybound.api.openapi.client.handler.*
import com.ssegning.keycloak.keybound.helper.noop
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

    override fun close() = noop()
}
