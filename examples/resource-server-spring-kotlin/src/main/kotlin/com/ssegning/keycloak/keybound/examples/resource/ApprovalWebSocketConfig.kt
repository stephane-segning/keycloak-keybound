package com.ssegning.keycloak.keybound.examples.resource

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class ApprovalWebSocketConfig(
    private val approvalStreamWebSocketHandler: ApprovalStreamWebSocketHandler,
    private val approvalStreamAuthInterceptor: ApprovalStreamAuthInterceptor
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(approvalStreamWebSocketHandler, "/ws/approvals")
            .setAllowedOriginPatterns("*")
            .addInterceptors(approvalStreamAuthInterceptor)
    }
}
