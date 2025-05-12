// This class shows how to use a custom model.
@file:Suppress("CdiInjectionPointsInspection", "TooGenericExceptionCaught")

package io.quarkiverse.langchain4j.sample.chatbot.sentiment

import dev.langchain4j.data.message.SystemMessage.systemMessage
import dev.langchain4j.data.message.UserMessage.userMessage
import dev.langchain4j.kotlin.model.chat.chat
import dev.langchain4j.model.chat.ChatModel
import io.quarkiverse.langchain4j.sample.chatbot.Question
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped

private val systemMessage = systemMessage(
    """
        Analyze sentiment of given user message.
        Return one of following words and nothing else: "POSITIVE", "NEUTRAL", "NEGATIVE"
    """.trimIndent()
)

@ApplicationScoped
class SentimentAnalyzer(
    private val chatModel: ChatModel,
) {

    suspend fun analyzeSentiment(text: Question): Sentiment {
        Log.trace("Analyzing sentiment of: \"$text\"")
        chatModel.chat {
            messages += systemMessage
            messages += userMessage(text)
            parameters {
                modelName = "gpt-4.1-nano"
            }
        }.let {
            val reply = it.aiMessage().text()
            return try {
                Sentiment.valueOf(reply)
            } catch (_: Exception) {
                Log.warn("Unexpected sentiment reply: `$reply`. Returning ${Sentiment.NEUTRAL}")
                Sentiment.NEUTRAL
            }
        }

    }
}
