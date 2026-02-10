package com.ssegning.keycloak.keybound.examples.backend.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.Module
import org.openapitools.jackson.nullable.JsonNullableModule
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfig {
    @Bean
    fun jsonNullableModule(): Module = JsonNullableModule()

    @Bean
    fun jsonNullableCustomizer(): Jackson2ObjectMapperBuilderCustomizer {
        return Jackson2ObjectMapperBuilderCustomizer { builder ->
            builder.serializationInclusion(JsonInclude.Include.NON_ABSENT)
        }
    }
}
