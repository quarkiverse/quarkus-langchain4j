package io.quarkiverse.langchain4j.sample.chatbot

import io.quarkus.logging.Log
import kotlinx.coroutines.withContext

suspend fun Assistant.chatAsync(
    memoryId: ChatMemoryId,
    question: Question,
    userInfo: Map<String, Any>,
    dispatcher: kotlinx.coroutines.CoroutineDispatcher,
): Answer {
    val assistant = this
    return withContext(dispatcher) {
        Log.debug("Processing question: $question")
        assistant.chat(memoryId, question, userInfo)
    }
}
