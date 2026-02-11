package com.ssegning.keycloak.keybound.examples.resource

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64

@Configuration
@EnableWebSecurity
class SecurityConfig {
    companion object {
        private val log = LoggerFactory.getLogger(SecurityConfig::class.java)
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtDecoder: JwtDecoder
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests {
                it.requestMatchers("/health", "/ws/**").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt -> jwt.decoder(jwtDecoder) }
            }

        return http.build()
    }

    @Bean
    fun jwtDecoder(
        @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}") issuerUri: String,
        @Value("\${jwt.clock-skew-seconds:300}") clockSkewSeconds: Long
    ): JwtDecoder {
        // Explicit decoder/validators for consistent behavior across Spring versions.
        val delegate = NimbusJwtDecoder.withIssuerLocation(issuerUri)
            .build()
        val validator = DelegatingOAuth2TokenValidator(
            JwtIssuerValidator(issuerUri),
            JwtTimestampValidator(Duration.ofSeconds(clockSkewSeconds))
        )
        delegate.setJwtValidator(validator)

        return JwtDecoder { token ->
            try {
                delegate.decode(token)
            } catch (exception: JwtException) {
                log.error(
                    "JWT decode failed issuerUri={} skewSeconds={} reason={} tokenHeader={} tokenPayload={}",
                    issuerUri,
                    clockSkewSeconds,
                    exception.message,
                    decodeJwtPart(token, 0),
                    decodeJwtPart(token, 1)
                )
                throw exception
            }
        }
    }

    private fun decodeJwtPart(token: String, index: Int): String {
        val parts = token.split(".")
        if (parts.size <= index) {
            return "<missing>"
        }
        return try {
            val normalized = parts[index]
                .replace('-', '+')
                .replace('_', '/')
                .let { value -> value + "=".repeat((4 - value.length % 4) % 4) }
            String(Base64.getDecoder().decode(normalized), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            "<decode-error>"
        }
    }
}
