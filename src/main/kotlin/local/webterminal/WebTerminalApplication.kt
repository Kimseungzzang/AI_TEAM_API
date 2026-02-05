package local.webterminal

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class WebTerminalApplication

fun main(args: Array<String>) {
    SpringApplication.run(WebTerminalApplication::class.java, *args)
}
