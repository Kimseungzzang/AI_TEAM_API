package local.webterminal.config

import local.webterminal.controller.TerminalProxyWsHandler
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WsConfig(private val terminalProxyWsHandler: TerminalProxyWsHandler) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(terminalProxyWsHandler, "/ws/terminal")
                .setAllowedOrigins(
                        "http://localhost:8080",
                        "http://127.0.0.1:8080",
                        "http://localhost:3000",
                        "http://127.0.0.1:3000"
                )
    }
}
