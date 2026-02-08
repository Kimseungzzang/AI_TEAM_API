package local.webterminal

import java.util.concurrent.TimeUnit

/**
 * CLI 실행 독립 테스트
 *
 * 실행: ./gradlew runCliTest
 */
fun main() {
    println("=== CLI Execution Test ===\n")

    // 1. Echo 테스트 (기본 ProcessBuilder 동작 확인)
    println("1. Echo Test")
    testEcho()

    // 2. CLI 존재 확인
    println("\n2. CLI Availability Check")
    val claudeExists = checkCliExists("claude")
    val codexExists = checkCliExists("codex")
    val geminiExists = checkCliExists("gemini")

    println("   claude: ${if (claudeExists) "✓ Found" else "✗ Not found"}")
    println("   codex:  ${if (codexExists) "✓ Found" else "✗ Not found"}")
    println("   gemini: ${if (geminiExists) "✓ Found" else "✗ Not found"}")

    // 3. Claude 테스트 (설치된 경우)
    if (claudeExists) {
        println("\n3. Claude CLI Test")
        testClaude()
    }

    // 4. Codex 테스트 (설치된 경우)
    if (codexExists) {
        println("\n4. Codex CLI Test")
        testCodex()
    }

    println("\n=== Test Complete ===")
}

fun testEcho() {
    try {
        val process = ProcessBuilder("echo", "HELLO_CLI_TEST")
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()

        if (exitCode == 0 && output.contains("HELLO_CLI_TEST")) {
            println("   ✓ Echo test passed: $output")
        } else {
            println("   ✗ Echo test failed: exitCode=$exitCode, output=$output")
        }
    } catch (e: Exception) {
        println("   ✗ Echo test error: ${e.message}")
    }
}

fun checkCliExists(cli: String): Boolean {
    return try {
        val process = ProcessBuilder("which", cli)
            .redirectErrorStream(true)
            .start()
        process.waitFor() == 0
    } catch (e: Exception) {
        false
    }
}

fun testClaude() {
    try {
        val prompt = "Respond with only: TEST_OK"
        println("   Prompt: $prompt")
        println("   Running claude CLI...")

        val process = ProcessBuilder("claude", "-p", prompt, "--output-format", "json")
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val completed = process.waitFor(60, TimeUnit.SECONDS)

        if (!completed) {
            process.destroyForcibly()
            println("   ✗ Timeout after 60s")
            return
        }

        println("   Exit code: ${process.exitValue()}")
        println("   Output (first 500 chars):")
        println("   ${output.take(500)}")

        if (output.contains("TEST_OK") || output.contains("result") || output.contains("response")) {
            println("   ✓ Claude test passed")
        } else {
            println("   ? Claude returned unexpected output")
        }
    } catch (e: Exception) {
        println("   ✗ Claude test error: ${e.message}")
    }
}

fun testCodex() {
    try {
        val prompt = "echo TEST_OK"
        println("   Prompt: $prompt")
        println("   Running codex CLI...")

        val process = ProcessBuilder("codex", "exec", "-")
            .redirectErrorStream(true)
            .start()

        // codex uses stdin
        process.outputStream.bufferedWriter().use { writer ->
            writer.write(prompt)
            writer.newLine()
        }

        val output = process.inputStream.bufferedReader().readText()
        val completed = process.waitFor(60, TimeUnit.SECONDS)

        if (!completed) {
            process.destroyForcibly()
            println("   ✗ Timeout after 60s")
            return
        }

        println("   Exit code: ${process.exitValue()}")
        println("   Output (first 500 chars):")
        println("   ${output.take(500)}")

        println("   ✓ Codex test completed")
    } catch (e: Exception) {
        println("   ✗ Codex test error: ${e.message}")
    }
}
