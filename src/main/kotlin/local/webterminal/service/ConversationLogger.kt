package local.webterminal.service

import java.io.BufferedWriter
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

object ConversationLogger {
    private val LOG_PATH: Path = Path.of(System.getProperty("user.dir"), "conversation.md")
    private val LOCK = Any()

    fun logUser(text: String) {
        appendLine("user: $text")
    }

    fun logCodex(text: String) {
        appendLine("codex: $text")
    }

    private fun appendLine(line: String) {
        if (line.isBlank()) {
            return
        }
        synchronized(LOCK) {
            try {
                Files.newBufferedWriter(
                    LOG_PATH,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                ).use { writer ->
                    writer.write(line)
                    writer.newLine()
                }
            } catch (ignored: IOException) {
                // Ignore exception
            }
        }
    }
}
