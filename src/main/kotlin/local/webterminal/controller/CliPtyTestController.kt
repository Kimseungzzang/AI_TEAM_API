package local.webterminal.controller

import com.pty4j.PtyProcessBuilder
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RestController
class CliPtyTestController {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(CliPtyTestController::class.java)
    }

    data class CliPtyTestResponse(
        val output: String,
        val exitCode: Int?,
        val timeout: Boolean
    )

    @PostMapping("/api/cli/pty-test")
    fun runPtyTest(@RequestBody body: Map<String, Any?>): CliPtyTestResponse {
        val prompt = (body["prompt"] as? String)?.trim().orEmpty()
        require(prompt.isNotEmpty()) { "prompt is required" }

        val env = HashMap(System.getenv())
        if (env["TERM"].isNullOrBlank()) {
            env["TERM"] = "xterm-256color"
        }

        val command = arrayOf("claude", "-p", prompt)
        val builder = PtyProcessBuilder(command)
            .setEnvironment(env)
            .setDirectory(System.getProperty("user.home"))
            .setRedirectErrorStream(true)
            .setInitialColumns(120)
            .setInitialRows(30)

        LOGGER.info("PTY test start: cmd={}", command.joinToString(" "))
        val process = builder.start()

        val output = StringBuilder()
        val pool = Executors.newSingleThreadExecutor()
        val readerFuture = pool.submit {
            val inputStream = process.inputStream
            val buf = ByteArray(4096)
            while (true) {
                val read = inputStream.read(buf)
                if (read <= 0) break
                output.append(String(buf, 0, read, StandardCharsets.UTF_8))
            }
        }

        val timeoutSeconds = 90L
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
        }
        readerFuture.get(2, TimeUnit.SECONDS)
        pool.shutdownNow()

        val exitCode = if (finished) process.exitValue() else null
        LOGGER.info("PTY test done: exitCode={}, outputLen={}, timeout={}",
            exitCode, output.length, !finished)

        return CliPtyTestResponse(
            output = output.toString(),
            exitCode = exitCode,
            timeout = !finished
        )
    }
}
