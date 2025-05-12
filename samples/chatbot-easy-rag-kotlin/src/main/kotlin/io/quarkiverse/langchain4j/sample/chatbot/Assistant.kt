@file:Suppress("CdiManagedBeanInconsistencyInspection")

package io.quarkiverse.langchain4j.sample.chatbot

import dev.langchain4j.service.MemoryId
import dev.langchain4j.service.Moderate
import dev.langchain4j.service.ModerationException
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import io.quarkiverse.langchain4j.RegisterAiService
import io.quarkiverse.langchain4j.sample.chatbot.sentiment.Sentiment
import io.quarkiverse.langchain4j.sample.chatbot.tools.StockPrices
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.SessionScoped
import org.eclipse.microprofile.faulttolerance.Fallback


@RegisterAiService(
    tools = [StockPrices::class],
    // no need to declare a retrieval augmentor here, it is automatically generated and discovered
)
@SessionScoped
@Suppress("unused", "kotlin:S6517")
@ApplicationScoped
interface Assistant {

    @UserMessage("Analyze sentiment of {{it}}")
    fun analyzeSentiment(text: Question): Sentiment

    @SystemMessage(
        """
            You are an AI named Bob answering questions about financial products.
            Your response must be polite, use the same language as the question, and be relevant to the question.

            When you don't know, respond that you don't know the answer and the bank will contact the customer directly.
            Make responses concise and to the point. Optimize for readability.

            Answer in English unless you are asked on another language.
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
