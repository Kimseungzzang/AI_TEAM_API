package local.webterminal

import com.pty4j.PtyProcessBuilder
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

@SpringBootTest
class Pty4jSpringTest {

    @Test
    fun testPty4jInSpringBoot() {
        println("\n${"=".repeat(60)}")
        println("Testing pty4j in Spring Boot context")
        println("=".repeat(60))

        val env = HashMap(System.getenv())
        env["TERM"] = "xterm-256color"

        val process = PtyProcessBuilder(arrayOf("/bin/sh"))
            .setEnvironment(env)
            .setRedirectErrorStream(true)
            .setInitialColumns(80)
            .setInitialRows(24)
            .start()

        println("Process started: isAlive=${process.isAlive}, pid=${process.pid()}")

        val output = StringBuilder()
        var running = true

        // Reader thread
        val readerThread = thread {
            try {
                val buf = ByteArray(4096)
                val inputStream = process.inputStream
                while (running && process.isAlive) {
                    val read = inputStream.read(buf)
                    if (read > 0) {
                        val chunk = String(buf, 0, read, StandardCharsets.UTF_8)
                        synchronized(output) {
                            output.append(chunk)
                        }
                        val preview = chunk.replace("\n", "\\n").replace("\r", "\\r").take(100)
                        println("RECV[$read bytes]: $preview")
                    } else if (read == -1) {
                        println("Stream closed")
                        break
                    }
                }
                println("Reader loop ended")
            } catch (e: Exception) {
                println("Reader error: ${e.message}")
            }
        }

        // Wait for shell to start
        Thread.sleep(1000)
        println("After 1s: isAlive=${process.isAlive}")

        // Send test command
        val testCmd = "echo HELLO_SPRING\n"
        println("SEND: ${testCmd.replace("\n", "\\n")}")
        process.outputStream.write(testCmd.toByteArray(StandardCharsets.UTF_8))
        process.outputStream.flush()
        println("Flush done")

        // Wait for response
        Thread.sleep(2000)
        println("After response wait: isAlive=${process.isAlive}")

        running = false
        process.destroyForcibly()
        readerThread.join(1000)

        val result = synchronized(output) { output.toString() }
        println("RESULT: ${if (result.contains("HELLO_SPRING")) "SUCCESS" else "FAIL"}")
        println("TOTAL OUTPUT[${result.length}]: ${result.take(300).replace("\n", "\\n").replace("\r", "\\r")}")

        assert(result.contains("HELLO_SPRING")) { "Expected HELLO_SPRING in output" }
    }
}
