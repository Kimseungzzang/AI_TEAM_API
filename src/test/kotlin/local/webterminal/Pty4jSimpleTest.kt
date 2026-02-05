package local.webterminal

import com.pty4j.PtyProcessBuilder
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class Pty4jSimpleTest {

    @Test
    fun testPty4jWithExecutorService() {
        println("\n${"=".repeat(60)}")
        println("Testing pty4j with ExecutorService (like WebSocket handler)")
        println("=".repeat(60))

        val env = HashMap(System.getenv())
        env["TERM"] = "xterm-256color"

        val process = PtyProcessBuilder(arrayOf("/bin/sh"))
            .setEnvironment(env)
            .setRedirectErrorStream(true)
            .setInitialColumns(120)
            .setInitialRows(40)
            .start()

        val ioPool = Executors.newFixedThreadPool(2)
        println("Process started: isAlive=${process.isAlive}, pid=${process.pid()}")

        val output = StringBuilder()
        var running = true

        // Reader in ioPool (like WebSocket handler)
        ioPool.submit {
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
                        println("[${Thread.currentThread().name}] RECV[$read bytes]: $preview")
                    } else if (read == -1) {
                        println("[${Thread.currentThread().name}] Stream closed")
                        break
                    }
                }
                println("[${Thread.currentThread().name}] Reader loop ended")
            } catch (e: Exception) {
                println("[${Thread.currentThread().name}] Reader error: ${e.message}")
            }
        }

        // Wait for shell to start
        Thread.sleep(1000)
        println("[main] After 1s: isAlive=${process.isAlive}")

        // Send from a different thread (like WebSocket HTTP thread)
        Thread {
            try {
                val testCmd = "echo HELLO_EXECUTOR\n"
                println("[${Thread.currentThread().name}] SEND: ${testCmd.replace("\n", "\\n")}")
                println("[${Thread.currentThread().name}] process.isAlive=${process.isAlive}")
                process.outputStream.write(testCmd.toByteArray(StandardCharsets.UTF_8))
                process.outputStream.flush()
                println("[${Thread.currentThread().name}] Flush done")
            } catch (e: Exception) {
                println("[${Thread.currentThread().name}] Send error: ${e.message}")
                e.printStackTrace()
            }
        }.start()

        // Wait for response
        Thread.sleep(2000)
        println("[main] After response wait: isAlive=${process.isAlive}")

        running = false
        ioPool.shutdownNow()
        process.destroyForcibly()

        val result = synchronized(output) { output.toString() }
        println("RESULT: ${if (result.contains("HELLO_EXECUTOR")) "SUCCESS" else "FAIL"}")
        println("TOTAL OUTPUT[${result.length}]: ${result.take(300).replace("\n", "\\n").replace("\r", "\\r")}")

        assert(result.contains("HELLO_EXECUTOR")) { "Expected HELLO_EXECUTOR in output" }
    }
}
