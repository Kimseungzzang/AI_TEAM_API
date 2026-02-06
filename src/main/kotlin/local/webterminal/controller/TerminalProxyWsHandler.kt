package local.webterminal.controller

import local.webterminal.dto.WsOutMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.TimeUnit

@Component
class TerminalProxyWsHandler(
    @Value("\${pty.proxy.url:ws://localhost:8091}") private val proxyUrl: String
) : TextWebSocketHandler() {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(TerminalProxyWsHandler::class.java)
        private val MAPPER = com.fasterxml.jackson.databind.ObjectMapper()
        private const val ATTR_DOWNSTREAM = "downstreamSession"
        private fun preview(text: String?, limit: Int = 160): String {
            if (text.isNullOrBlank()) {
                return ""
            }
            val normalized = text.replace("\n", "\\n").replace("\r", "\\r")
            return if (normalized.length <= limit) normalized else normalized.substring(0, limit) + "..."
        }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val client = StandardWebSocketClient()
        val downstreamHandler = DownstreamHandler(session)
        try {
            val future = client.doHandshake(downstreamHandler, proxyUrl)
            val downstream = future.get(5, TimeUnit.SECONDS)
            session.attributes[ATTR_DOWNSTREAM] = downstream
            LOGGER.info("WS proxy connected: FRONTEND={}, NODE={}, url={}", session.id, downstream.id, proxyUrl)
            downstream.attributes["upstreamId"] = session.id
            sendJson(session, "ws_ready", "ok")
            Thread {
                try {
                    Thread.sleep(3000)
                    if (session.isOpen) {
                        sendJson(session, "shell_ready", "ok")
                    }
                } catch (ignored: Exception) {
                    LOGGER.warn("WS proxy shell_ready delay failed: frontendId={}, error={}", session.id, ignored.message)
                }
            }.start()
        } catch (e: Exception) {
            LOGGER.error("WS proxy connect failed: upstream={}, url={}, error={}", session.id, proxyUrl, e.message, e)
            session.close(CloseStatus.SERVER_ERROR)
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val downstream = session.attributes[ATTR_DOWNSTREAM] as? WebSocketSession
        LOGGER.info("WS proxy recv: FRONTEND -> SPRING, frontendId={}, bytes={}, preview={}", session.id, message.payload.toByteArray().size, preview(message.payload))
        if (downstream == null || !downstream.isOpen) {
            LOGGER.warn("WS proxy drop: FRONTEND -> SPRING, frontendId={}, nodeOpen={}", session.id, downstream?.isOpen)
            return
        }
        try {
            downstream.sendMessage(message)
            LOGGER.info("WS proxy send: SPRING -> NODE, frontendId={}, nodeId={}, bytes={}, preview={}", session.id, downstream.id, message.payload.toByteArray().size, preview(message.payload))
        } catch (e: Exception) {
            LOGGER.error("WS proxy send failed: SPRING -> NODE, frontendId={}, nodeId={}, error={}", session.id, downstream.id, e.message, e)
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val downstream = session.attributes[ATTR_DOWNSTREAM] as? WebSocketSession
        if (downstream != null && downstream.isOpen) {
            downstream.close(status)
        }
        LOGGER.info("WS proxy closed: upstream={}, status={}", session.id, status)
    }

    private inner class DownstreamHandler(private val upstream: WebSocketSession) : TextWebSocketHandler() {
        override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
            if (!upstream.isOpen) {
                return
            }
            try {
                LOGGER.info("WS proxy recv: NODE -> SPRING, frontendId={}, nodeId={}, bytes={}, preview={}", upstream.id, session.id, message.payload.toByteArray().size, preview(message.payload))
                upstream.sendMessage(message)
                LOGGER.info("WS proxy send: SPRING -> FRONTEND, frontendId={}, bytes={}, preview={}", upstream.id, message.payload.toByteArray().size, preview(message.payload))
            } catch (e: Exception) {
                LOGGER.error("WS proxy forward failed: NODE -> FRONTEND, frontendId={}, nodeId={}, error={}", upstream.id, session.id, e.message, e)
            }
        }

        override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
            if (upstream.isOpen) {
                upstream.close(status)
            }
        }

    }

    private fun sendJson(session: WebSocketSession, type: String, data: String) {
        try {
            val json = MAPPER.writeValueAsString(WsOutMessage(type = type, data = data))
            session.sendMessage(TextMessage(json))
        } catch (ignored: Exception) {
            LOGGER.warn("WS proxy send json failed: frontendId={}, type={}, error={}", session.id, type, ignored.message)
        }
    }
}
