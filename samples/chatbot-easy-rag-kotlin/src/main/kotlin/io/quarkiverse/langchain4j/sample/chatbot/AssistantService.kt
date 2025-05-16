package io.quarkiverse.langchain4j.sample.chatbot

import io.quarkus.logging.Log
import io.quarkus.virtual.threads.VirtualThreads
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.asCoroutineDispatcher
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
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
    private val dispatcher = executorService.asCoroutineDispatcher()

    @Suppress("TooGenericExceptionCaught")
    suspend fun askQuestion(
        memoryId: ChatMemoryId,
        question: String,
    ): Answer = try {
        Log.info( "Processing question: $question")
        questionsEmitter.send(question)
        assistant.chatAsync(memoryId, question, dispatcher)
    } catch (e: Exception) {
        Log.error("Error while processing question: {}", arrayOf(question), e)
        fallbackAnswer
    }

}
