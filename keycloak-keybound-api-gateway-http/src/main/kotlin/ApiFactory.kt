package com.ssegning.keycloak.keybound.api

import com.ssegning.keycloak.keybound.api.openapi.client.handler.ApprovalsApi
import com.ssegning.keycloak.keybound.api.openapi.client.handler.DevicesApi
import com.ssegning.keycloak.keybound.api.openapi.client.handler.EnrollmentApi
import com.ssegning.keycloak.keybound.helper.noop
import com.ssegning.keycloak.keybound.models.HttpConfig
import com.ssegning.keycloak.keybound.spi.ApiGateway
import com.ssegning.keycloak.keybound.spi.ApiGatewayProviderFactory
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
        val config = HttpConfig.fromEnv(session.context)

        return Api(
            DevicesApi(config.baseUrl, config.client),
            ApprovalsApi(config.baseUrl, config.client),
            EnrollmentApi(config.baseUrl, config.client),
            config.baseUrl,
            config.client
        )
    }

    override fun init(config: Config.Scope) = noop()

    override fun postInit(factory: KeycloakSessionFactory) = noop()

    override fun close() = noop()

    override fun getId() = ID
}
