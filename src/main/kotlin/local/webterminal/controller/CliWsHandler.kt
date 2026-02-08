package local.webterminal.controller

import com.fasterxml.jackson.databind.ObjectMapper
import local.webterminal.dto.WsOutMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import com.pty4j.PtyProcessBuilder
import java.io.BufferedReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

@Component
class CliWsHandler : TextWebSocketHandler() {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(CliWsHandler::class.java)
        private val MAPPER = ObjectMapper()
        private const val ATTR_EXECUTOR = "executor"
        private val ANSI_PATTERN = Pattern.compile("""\u001B\[[0-?]*[ -/]*[@-~]""")
        private val OSC_PATTERN = Pattern.compile("""\u001B\][^\u0007]*\u0007""")
        private val OSC_ST_PATTERN = Pattern.compile("""\u001B\][^\u001B]*\u001B\\""")

        // Permission modes for each CLI
        private val CLAUDE_PERMISSIONS = mapOf(
            "default" to emptyList(),
            "plan" to listOf("--permission-mode", "plan"),
            "auto-edit" to listOf("--permission-mode", "acceptEdits"),
            "full-auto" to listOf("--permission-mode", "dontAsk"),
            "bypass-permissions" to listOf("--permission-mode", "bypassPermissions")
        )

        private val CODEX_PERMISSIONS = mapOf(
            "default" to emptyList<String>(),
            "suggest" to emptyList<String>(),
            "auto-edit" to emptyList<String>(),
            "full-auto" to emptyList<String>()
        )

        private val GEMINI_PERMISSIONS = mapOf(
            "default" to emptyList(),
            "yolo" to listOf("--yolo")
        )
    }

    private fun stripAnsi(text: String): String {
        var out = ANSI_PATTERN.matcher(text).replaceAll("")
        out = OSC_PATTERN.matcher(out).replaceAll("")
        out = OSC_ST_PATTERN.matcher(out).replaceAll("")
        return out
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val executor = Executors.newCachedThreadPool()
        session.attributes[ATTR_EXECUTOR] = executor
        LOGGER.info("WS connected: id={}", session.id)
        sendJson(session, "ws_ready", "ok")
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val executor = session.attributes[ATTR_EXECUTOR] as? ExecutorService ?: return

        val payload = message.payload
        LOGGER.info("WS recv: id={}, payload={}", session.id, payload.take(200))

        val request = try {
            MAPPER.readTree(payload)
        } catch (e: Exception) {
            LOGGER.warn("Invalid JSON: id={}, error={}", session.id, e.message)
            return
        }

        val type = request.get("type")?.asText() ?: ""
        if (type != "input") {
            return
        }

        val cli = request.get("cli")?.asText() ?: "claude"
        val data = request.get("data")?.asText() ?: ""
        val permission = request.get("permission")?.asText() ?: "default"

        if (data.isBlank()) {
            sendJson(session, "terminal", "[$cli] empty prompt\n")
            return
        }

        executor.submit {
            runCli(session, cli, data.trim(), permission)
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val executor = session.attributes[ATTR_EXECUTOR] as? ExecutorService
        executor?.shutdownNow()
        LOGGER.info("WS closed: id={}, status={}", session.id, status)
    }

    private fun runCli(session: WebSocketSession, cli: String, prompt: String, permission: String) {
        LOGGER.info("CLI start: id={}, cli={}, prompt={}, permission={}", session.id, cli, prompt.take(100), permission)

        try {
            if (cli == "claude" && !hasClaudeKey()) {
                LOGGER.warn("Claude API key not found; relying on CLI login session.")
            }

            val command = buildCommand(cli, prompt, permission)
            LOGGER.info("CLI command: id={}, cli={}, cmd={}", session.id, cli, command.joinToString(" "))

            val isClaude = cli == "claude"
            val process = if (isClaude) {
                val env = HashMap(System.getenv())
                if (env["TERM"].isNullOrBlank()) {
                    env["TERM"] = "xterm-256color"
                }
                val builder = PtyProcessBuilder(command.toTypedArray())
                    .setEnvironment(env)
                    .setDirectory(System.getProperty("user.home"))
                    .setRedirectErrorStream(true)
                    .setInitialColumns(120)
                    .setInitialRows(30)
                builder.start()
            } else {
                val processBuilder = ProcessBuilder(command)
                    .redirectErrorStream(true)
                if (cli == "gemini") {
                    // Suppress Node deprecation warnings from gemini CLI
                    processBuilder.environment()["NODE_OPTIONS"] = "--no-deprecation --no-warnings"
                }
                processBuilder.start()
            }

            val timeoutSeconds = if (cli == "claude") 90L else 120L

            if (cli == "claude") {
                Thread {
                    try {
                        Thread.sleep(5000)
                        if (process.isAlive) {
                            LOGGER.info("CLI still running: id={}, cli={}", session.id, cli)
                        }
                    } catch (_: Exception) {
                    }
                }.start()
            }

            // codex uses stdin for prompt
            if (cli == "codex") {
                process.outputStream.bufferedWriter().use { writer ->
                    writer.write(prompt)
                    writer.newLine()
                }
            }

            val output = StringBuilder()
            process.inputStream.bufferedReader().use { reader ->
                val buf = CharArray(2048)
                var read = reader.read(buf)
                while (read > 0) {
                    output.append(buf, 0, read)
                    read = reader.read(buf)
                }
            }

            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                sendJson(session, "terminal", "[$cli] timeout after ${timeoutSeconds}s\n")
                return
            }

            val rawOutput = output.toString()
            logRawOutput(cli, rawOutput)

            val result = parseOutput(cli, rawOutput)
            sendJson(session, "terminal", result.ifBlank { "[$cli] (no output, exit ${process.exitValue()})" })

            LOGGER.info("CLI done: id={}, cli={}, exitCode={}, outputLen={}",
                session.id, cli, process.exitValue(), result.length)

        } catch (e: Exception) {
            LOGGER.error("CLI error: id={}, cli={}, error={}", session.id, cli, e.message, e)
            sendJson(session, "terminal", "[$cli] error: ${e.message}\n")
        }
    }

    private fun buildCommand(cli: String, prompt: String, permission: String): List<String> {
        return when (cli) {
            "claude" -> {
                listOf("claude", "-p", prompt)
            }
            "codex" -> {
                val permFlags = CODEX_PERMISSIONS[permission] ?: emptyList()
                listOf("codex", "exec", "--json", "-") + permFlags
            }
            "gemini" -> {
                val permFlags = GEMINI_PERMISSIONS[permission] ?: emptyList()
                listOf("gemini", "-p", prompt, "--output-format", "json") + permFlags
            }
            else -> listOf("claude", "-p", prompt, "--output-format", "json")
        }
    }

    private fun parseOutput(cli: String, stdout: String): String {
        return when (cli) {
            "codex" -> parseCodexOutput(stdout)
            "gemini" -> parseGeminiOutput(stdout)
            else -> parseClaudeOutput(stdout)
        }
    }

    private fun parseClaudeOutput(stdout: String): String {
        val obj = parseJsonFromMixed(stdout, setOf("result", "response", "content"))
        return if (obj != null) {
            obj.get("result")?.asText()
                ?: obj.get("response")?.asText()
                ?: extractContent(obj.get("message")?.get("content"))
                ?: extractContent(obj.get("content"))
                ?: stripAnsi(stdout)
        } else {
            stripAnsi(stdout)
        }
    }

    private fun parseGeminiOutput(stdout: String): String {
        val obj = parseJsonFromMixed(stdout, setOf("response", "content"))
        return if (obj != null) {
            obj.get("response")?.asText()
                ?: extractContent(obj.get("message")?.get("content"))
                ?: extractContent(obj.get("content"))
                ?: stdout
        } else {
            LOGGER.warn("Gemini parse failed: no JSON found")
            stdout
        }
    }

    private fun parseCodexOutput(stdout: String): String {
        // Codex --json outputs JSONL format
        // Look for: {"type":"item.completed","item":{"type":"agent_message","text":"..."}}
        val lines = stdout.lines()
            .map { it.trim() }
            .filter { it.startsWith("{") && it.endsWith("}") }

        val messages = mutableListOf<String>()
        for (line in lines) {
            try {
                val obj = MAPPER.readTree(line)
                val type = obj.get("type")?.asText()

                // Look for item.completed events with agent_message
                if (type == "item.completed") {
                    val item = obj.get("item")
                    val itemType = item?.get("type")?.asText()
                    if (itemType == "agent_message") {
                        val text = item.get("text")?.asText()
                        if (!text.isNullOrBlank()) {
                            messages.add(text)
                        }
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }

        // Return all agent messages joined, or fallback to raw output
        return if (messages.isNotEmpty()) {
            messages.joinToString("\n")
        } else {
            // Fallback: try to extract any readable text
            stdout.lines()
                .filter { !it.startsWith("{") }
                .joinToString("\n")
                .ifBlank { stdout }
        }
    }

    private fun extractContent(content: com.fasterxml.jackson.databind.JsonNode?): String? {
        if (content == null) return null
        if (content.isTextual) return content.asText()
        if (content.isArray) {
            return content.mapNotNull {
                if (it.isTextual) it.asText()
                else it.get("text")?.asText()
            }.joinToString("")
        }
        return content.get("text")?.asText()
    }

    private fun parseClaudeStreamChunk(line: String): String? {
        if (line.isBlank()) return null
        return try {
            val obj = MAPPER.readTree(line)
            val type = obj.get("type")?.asText()
            if (type == "content_block_delta") {
                val delta = obj.get("delta")
                delta?.get("text")?.asText()
            } else if (type == "message") {
                extractContent(obj.get("content"))
            } else if (type == "message_delta") {
                extractContent(obj.get("delta"))
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseJsonFromMixed(
        stdout: String,
        requiredFields: Set<String>
    ): com.fasterxml.jackson.databind.JsonNode? {
        if (stdout.isBlank()) return null
        // 1) Try whole string
        try {
            val obj = MAPPER.readTree(stdout)
            if (containsAnyField(obj, requiredFields)) return obj
        } catch (_: Exception) {
        }

        // 2) Extract balanced JSON blocks and pick the last matching one
        val candidates = extractJsonBlocks(stdout)
        for (i in candidates.indices.reversed()) {
            val candidate = candidates[i]
            try {
                val obj = MAPPER.readTree(candidate)
                if (containsAnyField(obj, requiredFields)) return obj
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private fun extractJsonBlocks(text: String): List<String> {
        val blocks = mutableListOf<String>()
        var depth = 0
        var start = -1
        text.forEachIndexed { idx, ch ->
            if (ch == '{') {
                if (depth == 0) start = idx
                depth += 1
            } else if (ch == '}') {
                if (depth > 0) {
                    depth -= 1
                    if (depth == 0 && start >= 0) {
                        blocks.add(text.substring(start, idx + 1))
                        start = -1
                    }
                }
            }
        }
        return blocks
    }

    private fun containsAnyField(node: com.fasterxml.jackson.databind.JsonNode, fields: Set<String>): Boolean {
        for (field in fields) {
            if (node.has(field)) return true
        }
        return false
    }

    private fun hasClaudeKey(): Boolean {
        val env = System.getenv()
        val key = env["ANTHROPIC_API_KEY"] ?: env["CLAUDE_API_KEY"]
        return !key.isNullOrBlank()
    }

    private fun sendJson(session: WebSocketSession, type: String, data: String) {
        if (!session.isOpen) return
        try {
            val json = MAPPER.writeValueAsString(WsOutMessage(type = type, data = data))
            session.sendMessage(TextMessage(json))
            LOGGER.debug("WS send: id={}, type={}, len={}", session.id, type, data.length)
        } catch (e: Exception) {
            LOGGER.warn("WS send failed: id={}, error={}", session.id, e.message)
        }
    }

    private fun logRawOutput(cli: String, stdout: String) {
        if (stdout.isBlank()) {
            LOGGER.warn("CLI output empty: cli={}", cli)
            return
        }
        val head = stdout.take(500).replace("\n", "\\n")
        val tail = stdout.takeLast(500).replace("\n", "\\n")
        LOGGER.info("CLI raw output: cli={}, len={}, head={}, tail={}", cli, stdout.length, head, tail)
    }
}
