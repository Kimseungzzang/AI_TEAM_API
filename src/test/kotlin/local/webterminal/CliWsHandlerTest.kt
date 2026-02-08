package local.webterminal

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import jakarta.websocket.*

/**
 * CliWsHandler WebSocket 테스트
 *
 * 실행 전 Spring Boot 서버가 실행 중이어야 함:
 * ./gradlew bootRun
 *
 * 또는 @SpringBootTest로 통합 테스트 가능
 */
class CliWsHandlerTest {

    private val mapper = ObjectMapper()
    private val wsUrl = "ws://localhost:8080/ws/terminal"

    /**
     * WebSocket 연결 테스트
     * - 연결 성공 시 ws_ready 메시지 수신 확인
     */
    @Test
    @Disabled("서버 실행 필요")
    fun testWebSocketConnection() {
        val latch = CountDownLatch(1)
        var receivedWsReady = false

        val client = ContainerProvider.getWebSocketContainer()
        val session = client.connectToServer(object : Endpoint() {
            override fun onOpen(session: Session, config: EndpointConfig) {
                session.addMessageHandler(String::class.java) { message ->
                    println("Received: $message")
                    try {
                        val json = mapper.readTree(message)
                        if (json.get("type")?.asText() == "ws_ready") {
                            receivedWsReady = true
                            latch.countDown()
                        }
                    } catch (e: Exception) {
                        println("Parse error: ${e.message}")
                    }
                }
            }
        }, ClientEndpointConfig.Builder.create().build(), URI(wsUrl))

        val connected = latch.await(5, TimeUnit.SECONDS)
        session.close()

        assertTrue(connected, "Should receive ws_ready within 5 seconds")
        assertTrue(receivedWsReady, "Should receive ws_ready message")
    }

    /**
     * Echo 명령 테스트 (CLI 없이 테스트 가능)
     * - echo 명령으로 ProcessBuilder 동작 확인
     */
    @Test
    fun testProcessBuilderEcho() {
        val process = ProcessBuilder("echo", "HELLO_TEST")
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        assertEquals(0, exitCode, "Echo should exit with 0")
        assertTrue(output.contains("HELLO_TEST"), "Output should contain HELLO_TEST")
        println("Echo output: $output")
    }

    /**
     * Claude CLI 존재 확인 테스트
     */
    @Test
    fun testClaudeCliExists() {
        val process = ProcessBuilder("which", "claude")
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode == 0) {
            println("Claude CLI found at: $output")
            assertTrue(output.isNotBlank(), "Claude path should not be blank")
        } else {
            println("Claude CLI not found - skipping CLI tests")
        }
    }

    /**
     * Claude CLI 실행 테스트
     * - 실제 Claude CLI가 설치되어 있어야 함
     */
    @Test
    @Disabled("Claude CLI 설치 필요")
    fun testClaudeCliExecution() {
        val prompt = "Say 'HELLO_TEST' and nothing else."
        val process = ProcessBuilder("claude", "-p", prompt, "--output-format", "json")
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val completed = process.waitFor(60, TimeUnit.SECONDS)

        assertTrue(completed, "Should complete within 60 seconds")
        println("Claude output: $output")

        // JSON 파싱 테스트
        try {
            val json = mapper.readTree(output)
            assertNotNull(json, "Should return valid JSON")
            println("Parsed JSON: $json")
        } catch (e: Exception) {
            println("JSON parse failed: ${e.message}")
        }
    }

    /**
     * WebSocket CLI 통합 테스트
     * - 서버 실행 + Claude CLI 설치 필요
     */
    @Test
    @Disabled("서버 실행 + Claude CLI 설치 필요")
    fun testWebSocketCliIntegration() {
        val latch = CountDownLatch(2) // ws_ready + terminal response
        val messages = mutableListOf<String>()

        val client = ContainerProvider.getWebSocketContainer()
        val session = client.connectToServer(object : Endpoint() {
            override fun onOpen(session: Session, config: EndpointConfig) {
                session.addMessageHandler(String::class.java) { message ->
                    println("Received: $message")
                    messages.add(message)

                    try {
                        val json = mapper.readTree(message)
                        val type = json.get("type")?.asText()

                        when (type) {
                            "ws_ready" -> {
                                // Send CLI request
                                val request = mapOf(
                                    "type" to "input",
                                    "cli" to "claude",
                                    "data" to "Say 'INTEGRATION_TEST_OK' and nothing else."
                                )
                                session.basicRemote.sendText(mapper.writeValueAsString(request))
                                latch.countDown()
                            }
                            "terminal" -> {
                                latch.countDown()
                            }
                        }
                    } catch (e: Exception) {
                        println("Error: ${e.message}")
                    }
                }
            }
        }, ClientEndpointConfig.Builder.create().build(), URI(wsUrl))

        val completed = latch.await(120, TimeUnit.SECONDS)
        session.close()

        assertTrue(completed, "Should receive response within 120 seconds")
        assertTrue(messages.size >= 2, "Should receive at least 2 messages")
        println("All messages: $messages")
    }
}
