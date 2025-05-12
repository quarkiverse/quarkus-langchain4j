package io.quarkiverse.langchain4j.sample.chatbot.sentiment

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

suspend fun SentimentAnalyzer.analyzeSentimentAsync(
    text: String,
    dispatcher: CoroutineDispatcher,
): Sentiment {
    val assistant = this
    return withContext(dispatcher) {
        assistant.analyzeSentiment(text)
    }
}
