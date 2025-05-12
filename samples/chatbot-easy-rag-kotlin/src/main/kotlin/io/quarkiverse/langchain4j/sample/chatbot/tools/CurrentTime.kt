package io.quarkiverse.langchain4j.sample.chatbot.tools

import dev.langchain4j.agent.tool.Tool
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@ApplicationScoped
@OptIn(ExperimentalTime::class)
class CurrentTime {

    @Tool("returns current time", name = "time")
    fun currentTimeNow(): Instant = Clock.System.now()
}
