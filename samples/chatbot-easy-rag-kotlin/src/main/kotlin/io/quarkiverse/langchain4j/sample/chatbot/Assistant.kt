@file:Suppress("CdiManagedBeanInconsistencyInspection")

package io.quarkiverse.langchain4j.sample.chatbot

import dev.langchain4j.service.MemoryId
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import io.quarkiverse.langchain4j.RegisterAiService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.SessionScoped

@RegisterAiService(
) // no need to declare a retrieval augmentor here, it is automatically generated and discovered
@SessionScoped
@Suppress("kotlin:S6517")
@ApplicationScoped
interface Assistant {
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
    fun chat(
        @MemoryId memoryId: ChatMemoryId,
        @UserMessage question: String
    ): Answer

    @Suppress("unused")
    fun fallback(
        @MemoryId memoryId: ChatMemoryId,
        @UserMessage question: String
    ): Answer = Answer(
        message = """
            You have asked: \"$question\".
            I'm sorry, I can't help you with that question.
            Try again later.""".trimIndent(),
        links = listOf(
            Link("https://www.example.com/support", "Contact support(example.com)")
        )
    )
}
