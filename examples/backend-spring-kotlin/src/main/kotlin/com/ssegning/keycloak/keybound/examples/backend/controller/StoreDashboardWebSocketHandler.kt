package com.ssegning.keycloak.keybound.examples.backend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ssegning.keycloak.keybound.examples.backend.store.BackendDataStore
import org.slf4j.LoggerFactory
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

@Component
class StoreDashboardWebSocketHandler(
    private val store: BackendDataStore,
    private val storeDashboardScheduler: TaskScheduler,
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {
    private data class SessionState(
        var lastPayload: String? = null,
        var task: ScheduledFuture<*>? = null
    )

    companion object {
        private val log = LoggerFactory.getLogger(StoreDashboardWebSocketHandler::class.java)
        private val PUSH_INTERVAL: Duration = Duration.ofSeconds(1)
    }

    private val sessionStates = ConcurrentHashMap<String, SessionState>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val state = SessionState()
        sessionStates[session.id] = state
        pushSnapshotIfChanged(session, state)
        state.task = storeDashboardScheduler.scheduleAtFixedRate(
            { pushSnapshotIfChanged(session, state) },
            PUSH_INTERVAL
        )
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessionStates.remove(session.id)?.task?.cancel(true)
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        log.warn("Dashboard websocket transport error for session {}", session.id, exception)
        sessionStates.remove(session.id)?.task?.cancel(true)
    }

    private fun pushSnapshotIfChanged(session: WebSocketSession, state: SessionState) {
        if (!session.isOpen) {
            return
        }

        val snapshotPayload = objectMapper.writeValueAsString(store.snapshot())
        if (snapshotPayload == state.lastPayload) {
            return
        }

        val responsePayload = objectMapper.writeValueAsString(
            mapOf(
                "updatedAt" to OffsetDateTime.now().toString(),
                "snapshot" to objectMapper.readValue(snapshotPayload, Any::class.java)
            )
        )

        synchronized(session) {
            if (!session.isOpen) {
                return
            }

            session.sendMessage(TextMessage(responsePayload))
            state.lastPayload = snapshotPayload
        }
    }
}
