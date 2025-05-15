// This class shows how to use a custom model.
@file:Suppress("CdiInjectionPointsInspection", "TooGenericExceptionCaught")

package io.quarkiverse.langchain4j.sample.chatbot.sentiment

import dev.langchain4j.data.message.SystemMessage.systemMessage
import dev.langchain4j.data.message.UserMessage.userMessage
import dev.langchain4j.internal.Markers.SENSITIVE
import dev.langchain4j.kotlin.model.chat.chat
import dev.langchain4j.model.chat.ChatModel
import io.quarkiverse.langchain4j.sample.chatbot.Question
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(SentimentAnalyzer::class.java)

private val systemMessage = systemMessage(
    """
        Analyze sentiment of given user message.
        Return one of following words and nothing else: "POSITIVE", "NEUTRAL", "NEGATIVE"
    """.trimIndent()
)

@ApplicationScoped
class SentimentAnalyzer(
    private val chatModel: ChatModel,
    @ConfigProperty(name = "app.sentiment-analyzer.model-name")
    private val sentimentAnalyzerModelName: String,
) {

    suspend fun analyzeSentiment(text: Question): Sentiment {
        logger.trace(SENSITIVE, "Analyzing sentiment of: \"$text\"")
        chatModel.chat {
            messages += systemMessage
            messages += userMessage(text)
            parameters {
                modelName = sentimentAnalyzerModelName
            }
        }.let {
            val reply = it.aiMessage().text()
            return try {
                Sentiment.valueOf(reply)
            } catch (_: Exception) {
                logger.warn(
                    SENSITIVE, "Unexpected sentiment reply: `{}`. Returning {}",
                    reply, Sentiment.NEUTRAL
                )
                Sentiment.NEUTRAL
            }
        }

    }
}
