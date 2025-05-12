@file:Suppress("CdiManagedBeanInconsistencyInspection")

package io.quarkiverse.langchain4j.sample.chatbot.sentiment

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import io.quarkiverse.langchain4j.RegisterAiService
import io.quarkiverse.langchain4j.sample.chatbot.internal.NoopChatMemoryProvider
import io.quarkiverse.langchain4j.sample.chatbot.internal.NoopRetrievalAugmentor
import jakarta.enterprise.context.ApplicationScoped

@RegisterAiService(
    chatMemoryProviderSupplier = NoopChatMemoryProvider.Supplier::class,
    retrievalAugmentor = NoopRetrievalAugmentor.Supplier::class,
)
@Suppress("unused", "kotlin:S6517")
@ApplicationScoped
interface SentimentAnalyzer {

    @SystemMessage("Analyze sentiment of given sentence. Return only one word: POSITIVE, NEUTRAL or NEGATIVE")
    @UserMessage("The sentence: ```{{it}}```")
    fun analyzeSentiment(text: String): Sentiment
}
