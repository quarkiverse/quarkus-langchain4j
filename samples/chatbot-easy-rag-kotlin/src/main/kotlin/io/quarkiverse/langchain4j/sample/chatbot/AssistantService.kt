package io.quarkiverse.langchain4j.sample.chatbot

import io.quarkus.virtual.threads.VirtualThreads
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory.getLogger
import java.util.concurrent.ExecutorService

@Suppress("CdiInjectionPointsInspection")
@ApplicationScoped
class AssistantService(
    private val assistant: Assistant,
    @VirtualThreads
    private val executorService: ExecutorService
) {
    private val logger = getLogger(AssistantService::class.java)
    private val dispatcher = executorService.asCoroutineDispatcher()

    @Suppress("TooGenericExceptionCaught")
    suspend fun askQuestion(
        memoryId: ChatMemoryId,
        question: String,
    ): Answer {
        try {
            val answer = withContext(dispatcher) {
                assistant.chat(memoryId, question)
            }
            return answer
        } catch (e: Exception) {
            logger.error("Error while processing question: $question", e)
            return fallbackAnswer
        }
    }
}
