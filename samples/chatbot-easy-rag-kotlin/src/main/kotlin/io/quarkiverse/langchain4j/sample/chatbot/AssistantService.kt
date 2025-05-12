package io.quarkiverse.langchain4j.sample.chatbot

import dev.langchain4j.service.ModerationException
import io.quarkus.logging.Log
import io.quarkus.virtual.threads.VirtualThreads
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.asCoroutineDispatcher
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.util.concurrent.ExecutorService


@Suppress("CdiInjectionPointsInspection")
@ApplicationScoped
class AssistantService(
    private val assistant: Assistant,
    @Channel("questions")
    private val questionsEmitter: Emitter<Question>,
    @VirtualThreads
    executorService: ExecutorService
) {
    private val logger: Logger = getLogger(AssistantService::class.java)
    private val dispatcher = executorService.asCoroutineDispatcher()

    @Suppress("TooGenericExceptionCaught")
    suspend fun askQuestion(
        memoryId: ChatMemoryId,
        question: String,
    ): Answer = try {
        Log.info("Processing question: $question")
        questionsEmitter.send(question)
        assistant.chatAsync(memoryId, question, dispatcher, logger)
    } catch (e: ModerationException) {
        handleModerationException(e)
    } catch (e: Exception) {
        logger.error("Error while processing question: $question", e)
        fallbackAnswer
    }

    private fun handleModerationException(e: ModerationException): Answer {
        logger.warn(
            "Blocked user message due to content policy violation. Reason: {}",
            e.message,
            e
        )
        return Answer(
            message = "Sorry, your message couldn't be processed due to content guidelines."
        )
    }
}
