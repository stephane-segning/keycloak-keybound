package com.ssegning.keycloak.keybound.examples.backend.controller

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class StoreDashboardWebSocketConfig(
    private val storeDashboardWebSocketHandler: StoreDashboardWebSocketHandler
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(storeDashboardWebSocketHandler, "/ws/admin/stores")
            .setAllowedOriginPatterns("*")
    }
}
