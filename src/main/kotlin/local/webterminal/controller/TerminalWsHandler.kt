package local.webterminal.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import local.webterminal.service.ConversationLogger
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern

class TerminalWsHandler : TextWebSocketHandler() {

    companion object {
        private val MAPPER = ObjectMapper()
        private const val ATTR_PROCESS = "process"
        private const val ATTR_IOPOOL = "ioPool"
        private const val ATTR_INPUT_BUFFER = "inputBuffer"
        private const val ATTR_LAST_LOGGED = "lastLogged"
        private const val ATTR_OUTPUT_BUFFER = "outputBuffer"
        private const val ATTR_PENDING_ROLE = "pendingRole"
        private const val ATTR_LAST_ROLE_MSG = "lastRoleMsg"
        private const val ATTR_CLAUDE_READY = "claudeReady"

        private val ROLES = listOf(
            "Team Leader", "Planner", "Frontend Engineer", "Backend Engineer", "Designer"
        )

        private val ANSI_PATTERN = Pattern.compile("""\u001B\[[0-?]*[ -/]*[@-~]""")
        private val OSC_PATTERN = Pattern.compile("""\u001B\][^\u0007]*\u0007""")
        private val OSC_ST_PATTERN = Pattern.compile("""\u001B\][^\u001B]*\u001B\\""")

        private fun stripAnsi(s: String): String {
            var out = ANSI_PATTERN.matcher(s).replaceAll("")
            out = OSC_PATTERN.matcher(out).replaceAll("")
            out = OSC_ST_PATTERN.matcher(out).replaceAll("")
            return out
        }
    }


    override fun afterConnectionEstablished(session: WebSocketSession) {
        val env = HashMap(System.getenv())
        val process: PtyProcess = PtyProcessBuilder(arrayOf("cmd.exe"))
            .setEnvironment(env)
            .setRedirectErrorStream(true)
            .setInitialColumns(120)
            .setInitialRows(40)
            .setWindowsAnsiColorEnabled(true)
            .setUseWinConPty(true)
            .start()
        val ioPool: ExecutorService = Executors.newFixedThreadPool(2)
        session.attributes[ATTR_PROCESS] = process
        session.attributes[ATTR_IOPOOL] = ioPool
        session.attributes[ATTR_INPUT_BUFFER] = StringBuilder()
        session.attributes[ATTR_LAST_LOGGED] = ""
        session.attributes[ATTR_OUTPUT_BUFFER] = StringBuilder()
        session.attributes[ATTR_PENDING_ROLE] = null
        session.attributes[ATTR_LAST_ROLE_MSG] = HashMap<String, String>()
        session.attributes[ATTR_CLAUDE_READY] = false

        ioPool.submit {
            try {
                process.inputStream.use { `in` ->
                    val buf = ByteArray(4096)
                    var read: Int
                    while ((`in`.read(buf).also { read = it }) != -1 && session.isOpen) {
                        val chunk = String(buf, 0, read, StandardCharsets.UTF_8)
                        processAndSendOutput(session, chunk)
                    }
                }
            } catch (ignored: IOException) {
                // Ignore exception
            }
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val process = session.attributes[ATTR_PROCESS] as? PtyProcess ?: return

        val payload = message.payload
        val wsMessage = parseMessage(payload)

        if (wsMessage != null && "resize" == wsMessage.role) {
            if (wsMessage.data != null) {
                try {
                    val dataNode = MAPPER.readTree(wsMessage.data)
                    val cols = if (dataNode.has("cols")) dataNode["cols"].asInt() else 0
                    val rows = if (dataNode.has("rows")) dataNode["rows"].asInt() else 0
                    if (cols > 0 && rows > 0) {
                        process.setWinSize(WinSize(cols, rows))
                    }
                } catch (ignored: Exception) {
                    // Ignore exception
                }
            }
            if (wsMessage.cols != null && wsMessage.rows != null) {
                process.setWinSize(WinSize(wsMessage.cols, wsMessage.rows))
            }
            return
        }

        var input: String? = null
        if (wsMessage != null) {
            if ("input" == wsMessage.role || "input" == wsMessage.type) {
                input = wsMessage.data
            }
        }
        if (input == null) {
            input = payload
        }

        recordUserInput(session, input)
        val os = process.outputStream
        os.write(input!!.toByteArray(StandardCharsets.UTF_8))
        os.flush()
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val process = session.attributes[ATTR_PROCESS] as? PtyProcess
        val ioPool = session.attributes[ATTR_IOPOOL] as? ExecutorService
        process?.destroy()
        ioPool?.shutdownNow()
    }

    private fun recordUserInput(session: WebSocketSession, payload: String?) {
        if (payload.isNullOrBlank()) {
            return
        }
        val buf = session.attributes[ATTR_INPUT_BUFFER] as? StringBuilder ?: StringBuilder().also {
            session.attributes[ATTR_INPUT_BUFFER] = it
        }
        val len = payload.length
        for (i in 0 until len) {
            val c = payload[i]
            if (c == '\r' || c == '\n') {
                val line = buf.toString().trim()
                if (line.isNotEmpty()) {
                    ConversationLogger.logUser(line)
                }
                buf.setLength(0)
            } else {
                buf.append(c)
            }
        }
    }

    private fun processAndSendOutput(session: WebSocketSession, chunk: String?) {
        if (chunk.isNullOrBlank()) {
            return
        }

        sendJsonMessage(session, "terminal", chunk)

        val claudeReady = session.attributes[ATTR_CLAUDE_READY] as? Boolean ?: false
        if (!claudeReady) {
            if (looksLikeClaudePrompt(chunk)) {
                session.attributes[ATTR_CLAUDE_READY] = true
                sendJsonMessage(session, "ready", "true")
            }
        }

        parseAndSendRoleMessages(session, chunk)

        logOutputForConversation(session, chunk)
    }

    private fun parseAndSendRoleMessages(session: WebSocketSession, chunk: String?) {
        var cleaned = chunk?.let { stripAnsi(it) }
        if (cleaned.isNullOrBlank()) {
            return
        }

        cleaned = cleaned.replace("\r", "\n")

        val buffer = session.attributes[ATTR_OUTPUT_BUFFER] as? StringBuilder ?: StringBuilder().also {
            session.attributes[ATTR_OUTPUT_BUFFER] = it
        }

        buffer.append(cleaned)
        val lines = buffer.toString().split("\n".toRegex(), -1)
        buffer.setLength(0)
        buffer.append(lines.last())

        for (i in 0 until lines.size - 1) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue

            val parsed = parseRoleLine(session, line)
            if (parsed != null) {
                @Suppress("UNCHECKED_CAST")
                val lastMsgMap = session.attributes[ATTR_LAST_ROLE_MSG] as? MutableMap<String, String> ?: mutableMapOf<String, String>().also {
                    session.attributes[ATTR_LAST_ROLE_MSG] = it
                }
                val lastMsg = lastMsgMap[parsed.role]
                if (parsed.text != lastMsg) {
                    lastMsgMap[parsed.role] = parsed.text
                    sendJsonMessage(session, parsed.role, parsed.text)
                }
            }
        }
    }

    private fun logOutputForConversation(session: WebSocketSession, chunk: String?) {
        if (chunk.isNullOrBlank()) {
            return
        }
        var cleaned = stripAnsi(chunk)
        if (cleaned.isBlank()) {
            return
        }
        val lastCr = cleaned.lastIndexOf('\r')
        if (lastCr >= 0) {
            cleaned = cleaned.substring(lastCr + 1)
        }
        val logBuffer = StringBuilder()
        logBuffer.append(cleaned)
        var idx: Int
        while ((logBuffer.indexOf("\n").also { idx = it }) >= 0) {
            val line = logBuffer.substring(0, idx).replace("\r", "").trim()
            val content = extractCodexContent(line)
            if (content != null && shouldLogLine(session, content)) {
                ConversationLogger.logCodex(content)
            }
            logBuffer.delete(0, idx + 1)
        }
    }

    private fun shouldLogLine(session: WebSocketSession, line: String): Boolean {
        if (line.isEmpty()) {
            return false
        }
        val last = session.attributes[ATTR_LAST_LOGGED] as? String
        if (last != null && last == line) {
            return false
        }
        session.attributes[ATTR_LAST_LOGGED] = line
        return true
    }

    private fun extractCodexContent(line: String?): String? {
        if (line.isNullOrBlank()) {
            return null
        }
        val trimmed = line.trim()
        if (trimmed.isBlank()) {
            return null
        }
        val lower = trimmed.lowercase()
        return when {
            lower.startsWith("codex:") -> trimmed.substring(6).trim()
            lower.startsWith("codex ") -> trimmed.substring(6).trim()
            else -> null
        }
    }

    private fun parseMessage(payload: String?): WsMessage? {
        if (payload.isNullOrBlank()) {
            return null
        }
        val trimmed = payload.trim()
        return if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            null
        } else {
            try {
                MAPPER.readValue(trimmed, WsMessage::class.java)
            } catch (ignored: Exception) {
                null
            }
        }
    }

    private fun sendJsonMessage(session: WebSocketSession, role: String, data: String) {
        val node: ObjectNode = MAPPER.createObjectNode()
        node.put("role", role)
        node.put("data", data)
        try {
            session.sendMessage(TextMessage(MAPPER.writeValueAsString(node)))
        } catch (ignored: IOException) {
            // Ignore exception
        }
    }

    private fun looksLikeClaudePrompt(chunk: String?): Boolean {
        if (chunk.isNullOrBlank()) {
            return false
        }
        if (chunk.contains("requires git-bash") || chunk.contains("not recognized")) {
            return false
        }
        return chunk.contains("Welcome") ||
                chunk.contains("Claude Code v") ||
                chunk.contains("Try \"") ||
                chunk.contains("? for shortcuts")
    }

    private fun parseRoleLine(session: WebSocketSession, line: String?): ParsedRole? {
        if (line.isNullOrBlank()) {
            return null
        }
        val trimmed = line.trim()
        if (trimmed.isBlank()) {
            return null
        }
        if (isNoise(trimmed) || isGibberish(trimmed)) {
            return null
        }

        val roleOnly = extractRoleOnly(trimmed)
        if (roleOnly != null) {
            session.attributes[ATTR_PENDING_ROLE] = roleOnly
            return null
        }

        for (role in ROLES) {
            val idx = indexOfIgnoreCase(trimmed, "$role:")
            if (idx >= 0) {
                var text = trimmed.substring(idx + role.length + 1).trim()
                text = cleanLine(text)
                if (text.isBlank() || isSetupEcho(text) || isTrivialText(text) || isGibberish(text)) {
                    return null
                }
                return ParsedRole(role, text)
            }
        }

        val pendingRole = session.attributes[ATTR_PENDING_ROLE] as? String
        if (pendingRole != null) {
            val text = cleanLine(trimmed)
            if (text.isBlank() || isSetupEcho(text) || isTrivialText(text) || isGibberish(text)) {
                return null
            }
            session.attributes[ATTR_PENDING_ROLE] = null
            return ParsedRole(pendingRole, text)
        }

        return null
    }

    private fun extractRoleOnly(line: String?): String? {
        if (line.isNullOrBlank()) {
            return null
        }
        val trimmed = line.trim()
        if (trimmed.isBlank() || isNoise(trimmed)) {
            return null
        }
        val normalized = normalizeRoleLine(trimmed)
        for (role in ROLES) {
            if (normalized == role.lowercase()) {
                return role
            }
        }
        return null
    }

    private fun cleanLine(line: String?): String {
        if (line == null) {
            return ""
        }
        var text = line.replace("\u00A0", " ")
        text = text.replace(Regex("^[\\s\\u00A0]+"), "")
        text = text.replace(Regex("[\\s\\u00A0]+\$"), "")
        val noiseIdx = text.lowercase().indexOf("100% context left")
        if (noiseIdx >= 0) {
            text = text.substring(0, noiseIdx).trim()
        }
        return text
    }

    private fun isNoise(line: String?): Boolean {
        if (line.isNullOrBlank()) {
            return false
        }
        val lower = line.lowercase()
        if (lower.contains("context left") ||
            lower.contains("working(") ||
            lower.contains("esc to interrupt") ||
            lower.contains("tip:") ||
            lower.contains("claude") ||
            lower.contains("model:") ||
            lower.contains("directory:") ||
            lower.contains("use /skills") ||
            lower.contains("run /review") ||
            lower.contains("microsoft windows") ||
            lower.contains("all rights reserved") ||
            lower.contains("heads up") ||
            lower.contains("run /status")
        ) {
            return true
        }
        return lower.matches(Regex("^m+\$"))
    }

    private fun isSetupEcho(text: String?): Boolean {
        if (text.isNullOrBlank()) {
            return false
        }
        val lower = text.lowercase()
        return lower.startsWith("your name is \"") ||
                lower.contains("when you reply, always prefix your response")
    }

    private fun isTrivialText(text: String?): Boolean {
        if (text.isNullOrBlank()) {
            return true
        }
        val t = text.trim()
        if (t.isEmpty() || t.length < 2) {
            return true
        }
        if (t.matches(Regex("^['\"]+$"))) {
            return true
        }
        return t.matches(Regex("^[\\W_]+\$"))
    }

    private fun normalizeRoleLine(line: String?): String {
        if (line.isNullOrBlank()) {
            return ""
        }
        return line.replace(Regex("^['\"]+ |['\"]+ "), "")
            .replace(Regex("[:\\-\\s]+\$"), "")
            .trim()
            .lowercase()
    }

    private fun isGibberish(text: String?): Boolean {
        if (text.isNullOrBlank()) {
            return false
        }
        val t = text.trim()
        if (t.length < 20) {
            return false
        }
        val uniq = mutableSetOf<Char>()
        for (c in t) {
            uniq.add(c)
        }
        if (uniq.size <= 3) {
            return true
        }
        return !t.contains(" ") && t.length > 60
    }

    private fun indexOfIgnoreCase(haystack: String, needle: String): Int {
        return haystack.lowercase().indexOf(needle.lowercase())
    }

    private data class WsMessage(
        val type: String?, // legacy
        val role: String?, // new format
        val data: String?,
        val cols: Int?,
        val rows: Int?
    )

    private data class ParsedRole(
        val role: String,
        val text: String
    )
}
