package io.quarkiverse.langchain4j.sample.chatbot.processing

import io.quarkiverse.langchain4j.sample.chatbot.Question
import io.quarkiverse.langchain4j.sample.chatbot.internal.sendSuspending
import io.quarkiverse.langchain4j.sample.chatbot.sentiment.Sentiment
import io.quarkiverse.langchain4j.sample.chatbot.sentiment.SentimentAnalyzer
import io.quarkus.logging.Log
import io.quarkus.mailer.Mail
import io.quarkus.mailer.Mailer
import io.quarkus.virtual.threads.VirtualThreads
import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.asCoroutineDispatcher
import org.eclipse.microprofile.reactive.messaging.Incoming
import java.util.concurrent.ExecutorService

@ApplicationScoped
class QuestionProcessor(
    private val mailer: Mailer,
    private val sentimentAnalyzer: SentimentAnalyzer,
    @VirtualThreads
    executorService: ExecutorService
) {
    private val dispatcher = executorService.asCoroutineDispatcher()

    @Incoming("questions")
    @RunOnVirtualThread
    suspend fun process(question: Question) {
        Log.debug("Question received: $question")

        val sentiment = sentimentAnalyzer.analyzeSentiment(question)
        Log.debug("Sentiment: $sentiment")

        if (sentiment == Sentiment.NEGATIVE) {
            Log.info("Negative sentiment is detected. Sending email")
            mailer.sendSuspending(
                Mail.withText(
                    "security@horizonfinancial.example",
                    "WARNING: Negative sentiment detected",
                    """
                    Message:
                    $question
                """.trimIndent()
                ),
                dispatcher
            )
        }
    }
}
