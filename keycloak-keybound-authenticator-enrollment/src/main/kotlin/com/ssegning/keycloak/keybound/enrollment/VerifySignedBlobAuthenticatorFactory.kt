package com.ssegning.keycloak.keybound.enrollment

import com.ssegning.keycloak.keybound.authenticator.AbstractAuthenticatorFactory
import com.ssegning.keycloak.keybound.spi.ApiGateway
import org.keycloak.models.KeycloakSession

open class VerifySignedBlobAuthenticatorFactory : AbstractAuthenticatorFactory() {
    override fun create(session: KeycloakSession, apiGateway: ApiGateway) = VerifySignedBlobAuthenticator()

    override fun getId() = ID

    override fun getDisplayType() = "DK2- Verify Signed Blob"

    override fun getHelpText() = "Verifies the signed data captured by the previous step, including signature, timestamp, and nonce."

    companion object {
        const val ID = "verify-signed-blob"
    }
}
