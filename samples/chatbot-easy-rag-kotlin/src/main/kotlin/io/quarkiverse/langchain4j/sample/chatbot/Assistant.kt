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
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.SessionScoped
import org.eclipse.microprofile.faulttolerance.Fallback

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

    @SystemMessage(
        """
            You are an AI named Bob answering questions about financial products.
            First, greet the customer and ask how can you help.
            You may use only information from the documents and tools you have been provided.
            Your response must be polite, use the same language as the question, and be relevant to the question.
            Make responses concise and to the point. Optimize for readability.

            When you don't know, respond that you don't know the answer
            and offer link to the bank website and offer to organize a call
            with a financial advisor.

            When organizing a call, first, understand the ultimate customer problem.
            If you don't have required information to schedule a call - ask customer to provide it.
            The callback should be scheduled within 5 business days.
            Always get current date and time from tool.
            Use time format HH:mm, for example 13:00.
            Never schedule a callback for a date in the past.
            Get explicit confirmation from the customer before scheduling a call.
            Double check that all the necessary information is provided.
            If the customer does not want a callback, never schedule it.

            Answer only raw Markdown. Highlight numbers and important clauses.
            Do NOT emit any HTML tags.
            Do NOT wrap your answer in code fences or any other container.
            Do NOT include any explanatory text.
            """
    )
    @Moderate
    @Fallback(fallbackMethod = "chatFallback")
    fun chat(
        @MemoryId memoryId: ChatMemoryId,
        @UserMessage question: Question
    ): Answer

    fun chatFallback(
        @MemoryId memoryId: ChatMemoryId,
        @UserMessage question: Question,
        cause: Exception
    ): Answer {
        return if (cause is ModerationException) {
            Answer(
                message = """
                    Sorry, your message couldn't be processed due to content guidelines.
                    If you believe this is a mistake, please contact support.
                    """.trimIndent(),
                links = listOf(
                    Link("https://www.example.com/support", "Contact support(example.com)")
                )
            )
        } else {
            Answer(
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
}
