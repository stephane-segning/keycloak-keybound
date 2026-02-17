package com.ssegning.keycloak.keybound.api

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.module.SimpleModule
import com.ssegning.keycloak.keybound.api.openapi.client.handler.ApprovalsApi
import com.ssegning.keycloak.keybound.api.openapi.client.handler.DevicesApi
import com.ssegning.keycloak.keybound.api.openapi.client.handler.EnrollmentApi
import com.ssegning.keycloak.keybound.api.openapi.client.handler.UsersApi
import com.ssegning.keycloak.keybound.api.openapi.client.infrastructure.Serializer
import com.ssegning.keycloak.keybound.core.helper.SPI_CORE_INFO
import com.ssegning.keycloak.keybound.core.helper.noop
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import com.ssegning.keycloak.keybound.core.spi.ApiGatewayProviderFactory
import org.keycloak.Config
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.slf4j.LoggerFactory
import kotlin.time.Instant

open class ApiFactory : ApiGatewayProviderFactory {
    companion object {
        private val log = LoggerFactory.getLogger(ApiFactory::class.java)
        const val ID = "api-impl"
    }

    private class KotlinInstantDeserializer : JsonDeserializer<Instant>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Instant =
            Instant.parse(p.text)
    }

    private class KotlinInstantSerializer : JsonSerializer<Instant>() {
        override fun serialize(value: Instant, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeString(value.toString()) // ISO-8601, e.g. 2026-02-17T13:02:25.060234Z
        }
    }

    override fun create(session: KeycloakSession): ApiGateway {
        val client = SimpleCallFactory(session)
        val baseUrl = client.baseUrl

        log.debug("Creating ApiGateway with baseUrl={}", baseUrl)

        return Api(
            DevicesApi(basePath = baseUrl, client = client),
            ApprovalsApi(basePath = baseUrl, client = client),
            EnrollmentApi(basePath = baseUrl, client = client),
            UsersApi(basePath = baseUrl, client = client),
        )
    }

    override fun init(config: Config.Scope) = noop()

    fun kotlinTimeModule(): Module = SimpleModule("KotlinTimeModule")
        .addDeserializer(Instant::class.java, KotlinInstantDeserializer())
        .addSerializer(Instant::class.java, KotlinInstantSerializer())

    override fun postInit(factory: KeycloakSessionFactory) {
        Serializer.jacksonObjectMapper
            .registerModule(kotlinTimeModule())
    }

    override fun close() = noop()

    override fun getId() = ID

    override fun getOperationalInfo(): Map<String, String> = SPI_CORE_INFO
}
