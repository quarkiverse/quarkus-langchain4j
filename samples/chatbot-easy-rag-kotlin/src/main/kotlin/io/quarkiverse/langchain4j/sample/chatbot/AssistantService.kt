package io.quarkiverse.langchain4j.sample.chatbot

import dev.langchain4j.service.ModerationException
import io.quarkus.logging.Log
import io.quarkus.virtual.threads.VirtualThreads
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.asCoroutineDispatcher
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import java.util.concurrent.ExecutorService

@Suppress("CdiInjectionPointsInspection", "TooGenericExceptionCaught")
@ApplicationScoped
class AssistantService(
    private val assistant: Assistant,
    @Channel("questions")
    private val questionsEmitter: Emitter<Question>,
    @VirtualThreads
    executorService: ExecutorService
) {
    private val dispatcher = executorService.asCoroutineDispatcher()

    suspend fun askQuestion(
        memoryId: ChatMemoryId,
        question: Question,
        userInfo: Map<String, Any>,
    ): Answer = try {
        Log.info("Processing question: $question")
        questionsEmitter.send(question)
        assistant.chatAsync(memoryId, question, userInfo, dispatcher)
    } catch (_: ModerationException) {
        Log.info("[$memoryId] Question contains inappropriate content: $question")
        Answer(
            message = """
                    Sorry, your message couldn't be processed due to content guidelines.
                    If you believe this is a mistake, please contact out support team.
                    """.trimIndent(),
            links = listOf(
                Link("https://horizonfinancial.example/code-of-conduct", "Code of Conduct"),
                Link("https://horizonfinancial.example/support", "Contact Support")
            )
        )
    } catch (e: Exception) {
        Log.error("Error while processing question: {}", arrayOf(question), e)
        Answer(
            message = """
                    You have asked: \"$question\".
                    I'm sorry, I can't help you with that question.
                    Try again later.""".trimIndent(),
            links = listOf(
                Link("https://horizonfinancial.example/support", "Contact Support")
            )
        )
    }
}
