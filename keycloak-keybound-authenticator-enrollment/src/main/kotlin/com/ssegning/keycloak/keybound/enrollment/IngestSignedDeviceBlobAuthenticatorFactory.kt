package com.ssegning.keycloak.keybound.enrollment

import com.ssegning.keycloak.keybound.authenticator.AbstractAuthenticatorFactory
import com.ssegning.keycloak.keybound.spi.ApiGateway
import org.keycloak.models.KeycloakSession

open class IngestSignedDeviceBlobAuthenticatorFactory : AbstractAuthenticatorFactory() {
    override fun create(session: KeycloakSession, apiGateway: ApiGateway) =
        IngestSignedDeviceBlobAuthenticator()

    override fun getId() = ID

    override fun getDisplayType() = "DK1- Ingest Signed Device Blob"

    override fun getHelpText() =
        "Ingests a signed device blob from the request parameters and stores it in the authentication session notes."

    companion object {
        const val ID = "ingest-signed-device-blob"
    }
}
