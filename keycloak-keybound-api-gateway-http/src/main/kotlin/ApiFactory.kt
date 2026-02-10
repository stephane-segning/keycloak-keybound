package com.ssegning.keycloak.keybound.api

import com.ssegning.keycloak.keybound.api.openapi.client.handler.ApprovalsApi
import com.ssegning.keycloak.keybound.api.openapi.client.handler.DevicesApi
import com.ssegning.keycloak.keybound.api.openapi.client.handler.EnrollmentApi
import com.ssegning.keycloak.keybound.api.openapi.client.handler.UsersApi
import com.ssegning.keycloak.keybound.core.helper.SPI_CORE_INFO
import com.ssegning.keycloak.keybound.core.helper.noop
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import com.ssegning.keycloak.keybound.core.spi.ApiGatewayProviderFactory
import org.keycloak.Config
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.slf4j.LoggerFactory

open class ApiFactory : ApiGatewayProviderFactory {
    companion object {
        private val log = LoggerFactory.getLogger(ApiFactory::class.java)
        const val ID = "api-impl"
    }

    override fun create(session: KeycloakSession): ApiGateway {
        val client = SimpleCallFactory(session)
        val baseUrl = client.baseUrl

        return Api(
            DevicesApi(basePath = baseUrl, client = client),
            ApprovalsApi(basePath = baseUrl, client = client),
            EnrollmentApi(basePath = baseUrl, client = client),
            UsersApi(basePath = baseUrl, client = client)
        )
    }

    override fun init(config: Config.Scope) = noop()

    override fun postInit(factory: KeycloakSessionFactory) =
        noop()

    override fun close() = noop()

    override fun getId() = ID

    override fun getOperationalInfo(): Map<String, String> = SPI_CORE_INFO
}
