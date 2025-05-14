package io.quarkiverse.langchain4j.sample.chatbot.tools

import dev.langchain4j.agent.tool.Tool
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@ApplicationScoped
@OptIn(ExperimentalTime::class)
class CurrentTime(
    private val clock: Clock = Clock.System
) {

    @Tool("returns current date and time", name = "currentDateTime")
    fun currentDateTime(): String = "${clock.now()}"
}
