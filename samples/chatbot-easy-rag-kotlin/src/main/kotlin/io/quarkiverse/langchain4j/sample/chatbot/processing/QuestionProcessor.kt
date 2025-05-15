package io.quarkiverse.langchain4j.sample.chatbot.processing

import dev.langchain4j.internal.Markers.SENSITIVE
import io.quarkiverse.langchain4j.sample.chatbot.Question
import io.quarkiverse.langchain4j.sample.chatbot.internal.sendSuspending
import io.quarkiverse.langchain4j.sample.chatbot.sentiment.Sentiment
import io.quarkiverse.langchain4j.sample.chatbot.sentiment.SentimentAnalyzer
import io.quarkus.mailer.Mail
import io.quarkus.mailer.Mailer
import io.quarkus.virtual.threads.VirtualThreads
import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.asCoroutineDispatcher
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutorService

private val logger = LoggerFactory.getLogger(QuestionProcessor::class.java)

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
        logger.debug(SENSITIVE, "Question received: {}", question)

        val sentiment = sentimentAnalyzer.analyzeSentiment(question)
        logger.debug(SENSITIVE, "Sentiment: {}", sentiment)

        if (sentiment == Sentiment.NEGATIVE) {
            logger.info("Negative sentiment is detected. Sending email")
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
