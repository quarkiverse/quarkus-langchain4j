package io.quarkiverse.langchain4j.sample.chatbot.processing

import io.quarkiverse.langchain4j.sample.chatbot.Question
import io.quarkiverse.langchain4j.sample.chatbot.sentiment.analyzeSentimentAsync
import io.quarkiverse.langchain4j.sample.chatbot.sentiment.Sentiment
import io.quarkiverse.langchain4j.sample.chatbot.sentiment.SentimentAnalyzer
import io.quarkus.logging.Log
import io.quarkus.mailer.Mail
import io.quarkus.mailer.Mailer
import io.quarkus.virtual.threads.VirtualThreads
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.eclipse.microprofile.reactive.messaging.Incoming
import java.util.concurrent.ExecutorService

@ApplicationScoped
class QuestionProcessor(
    private val mailer: Mailer,
    private val sentimentAnalyzer: SentimentAnalyzer,
    @VirtualThreads
    private val executorService: ExecutorService
) {
    private val dispatcher = executorService.asCoroutineDispatcher()

    @Incoming("questions")
    suspend fun process(question: Question) {
        Log.info("Question received: $question")

        val sentiment = sentimentAnalyzer.analyzeSentimentAsync(question, dispatcher)
        Log.info("Sentiment: $sentiment")

        if (sentiment == Sentiment.NEGATIVE) {
            val mail = Mail().apply {
                from = "admin@hallofjustice.net"
                to = listOf("superheroes@quarkus.io")
                subject = "WARNING: Negative sentiment detected"
                text = """
                    Question:
                    $question
                """.trimIndent()
            }
            Log.info("Negative sentiment is detected. Sending email: $mail")
            withContext(dispatcher) {
                mailer.send(
                    Mail.withText(
                        "reporting@quarkus.io",
                        "WARNING: Negative sentiment detected",
                        """
                    Question:
                    $question
                """.trimIndent()
                    )
                )
//                mailer.send(mail)
            }

        }
    }
}
