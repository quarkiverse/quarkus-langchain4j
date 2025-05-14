@file:Suppress("CdiManagedBeanInconsistencyInspection")

package io.quarkiverse.langchain4j.sample.chatbot

import dev.langchain4j.service.MemoryId
import dev.langchain4j.service.Moderate
import dev.langchain4j.service.ModerationException
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import io.quarkiverse.langchain4j.RegisterAiService
import io.quarkiverse.langchain4j.sample.chatbot.tools.CurrentTime
import io.quarkiverse.langchain4j.sample.chatbot.tools.CustomerCallbackScheduler
import io.quarkiverse.langchain4j.sample.chatbot.tools.MarketData
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.SessionScoped
import org.eclipse.microprofile.faulttolerance.Fallback
import org.eclipse.microprofile.faulttolerance.Timeout
import java.time.temporal.ChronoUnit

@RegisterAiService(
    tools = [
        MarketData::class,
        CurrentTime::class,
        CustomerCallbackScheduler::class
    ],
    // no need to declare a retrieval augmentor here, it is automatically generated and discovered
)
@SessionScoped
@Suppress("unused", "kotlin:S6517")
@ApplicationScoped
interface Assistant {

    @SystemMessage(fromResource = "/prompts/assistant-system-prompt.md")
    @Moderate
    @Timeout(value = 60, unit = ChronoUnit.SECONDS)
    @Fallback(fallbackMethod = "chatFallback")
    fun chat(
        @MemoryId memoryId: ChatMemoryId,
        @UserMessage question: Question
    ): Answer

    fun chatFallback(
        @MemoryId memoryId: ChatMemoryId,
        @UserMessage question: Question,
        cause: ModerationException
    ): Answer = Answer(
        message = """
                    Sorry, your message couldn't be processed due to content guidelines.
                    If you believe this is a mistake, please contact support.
                    """.trimIndent(),
        links = listOf(
            Link("https://www.example.com/support", "Contact support(example.com)")
        )
    )

    fun chatFallback(
        @MemoryId memoryId: ChatMemoryId,
        @UserMessage question: Question,
        exception: Exception
    ): Answer {
        Log.warn("Error while processing question", exception)
        return Answer(
            message = """
                    You have asked: \"$question\".
                    I'm sorry, I can't help you with that question.
                    Try again later.""".trimIndent(),
            links = listOf(
                Link("https://www.example.com/support", "Contact support(example.com)")
            )
        )
    }
}
