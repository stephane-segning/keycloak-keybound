package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticatorFactory
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.models.KeycloakSession

open class SendValidateOtpAuthenticatorFactory : AbstractAuthenticatorFactory() {
    override fun create(
        session: KeycloakSession,
        apiGateway: ApiGateway,
    ) = SendValidateOtpAuthenticator()

    override fun getId() = ID

    override fun getDisplayType() = "DK8- Send & Validate OTP by SMS"

    override fun getHelpText() = "Sends an OTP challenge to the collected phone and validates the submitted code."

    companion object {
        const val ID = "send-validate-otp-sms"
    }
}
