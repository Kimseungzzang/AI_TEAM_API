package local.webterminal

import com.pty4j.PtyProcessBuilder
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

fun main() {
    val testCases = listOf(
        TestCase("cat", arrayOf("/bin/cat"), "HELLO\n", 500),
        TestCase("sh", arrayOf("/bin/sh"), "echo HELLO\n", 1000),
        TestCase("sh -i", arrayOf("/bin/sh", "-i"), "echo HELLO\n", 1000),
        TestCase("bash --noediting", arrayOf("/bin/bash", "--noediting"), "echo HELLO\n", 1000),
        TestCase("zsh -f", arrayOf("/bin/zsh", "-f"), "echo HELLO\n", 1000),
    )

    for (tc in testCases) {
        println("\n${"=".repeat(60)}")
        println("TEST: ${tc.name}")
        println("CMD: ${tc.cmd.joinToString(" ")}")
        println("=".repeat(60))

        try {
            runTest(tc)
        } catch (e: Exception) {
            println("ERROR: ${e.message}")
            e.printStackTrace()
        }

        Thread.sleep(500)
    }
}

data class TestCase(
    val name: String,
    val cmd: Array<String>,
    val input: String,
    val waitMs: Long
)

fun runTest(tc: TestCase) {
    val env = HashMap(System.getenv())
    env["TERM"] = "xterm-256color"

    val process = PtyProcessBuilder(tc.cmd)
        .setEnvironment(env)
        .setRedirectErrorStream(true)
        .setInitialColumns(80)
        .setInitialRows(24)
        .start()

    val output = StringBuilder()
    var running = true

    // 출력 읽기 스레드 (blocking read)
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
                    break
                }
            }
        } catch (e: Exception) {
            println("Reader error: ${e.message}")
        }
    }

    // 초기 출력 대기
    println("Waiting ${tc.waitMs}ms for shell to start...")
    Thread.sleep(tc.waitMs)

    // 입력 전송
    println("SEND: ${tc.input.replace("\n", "\\n").replace("\r", "\\r")}")
    val outputStream = process.outputStream
    outputStream.write(tc.input.toByteArray(StandardCharsets.UTF_8))
    outputStream.flush()
    println("Flush done")

    // 응답 대기
    println("Waiting 2000ms for response...")
    Thread.sleep(2000)

    running = false
    process.destroyForcibly()
    readerThread.join(1000)

    val result = synchronized(output) { output.toString() }
    println("RESULT: ${if (result.contains("HELLO")) "SUCCESS" else "FAIL"}")
    println("TOTAL OUTPUT[${result.length}]: ${result.take(300).replace("\n", "\\n").replace("\r", "\\r")}")
}
