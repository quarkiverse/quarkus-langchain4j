package io.quarkiverse.langchain4j.sample.chatbot

import kotlinx.coroutines.withContext
import org.slf4j.Logger

suspend fun Assistant.chatAsync(
    memoryId: ChatMemoryId,
    question: Question,
    dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    logger: Logger
): Answer {
    val assistant = this
    return withContext(dispatcher) {
        logger.debug("${Thread.currentThread().name} -  Processing question: $question")
        assistant.chat(memoryId, question)
    }
}
