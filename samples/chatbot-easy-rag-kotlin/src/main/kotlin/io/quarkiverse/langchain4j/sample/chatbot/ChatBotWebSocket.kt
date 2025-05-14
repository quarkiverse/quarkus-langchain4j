package io.quarkiverse.langchain4j.sample.chatbot

import io.quarkus.websockets.next.OnOpen
import io.quarkus.websockets.next.OnTextMessage
import io.quarkus.websockets.next.WebSocket
import io.quarkus.websockets.next.WebSocketConnection
import jakarta.enterprise.context.SessionScoped

@WebSocket(path = "/chatbot")
@Suppress("unused")
@SessionScoped
class ChatBotWebSocket(private val assistantService: AssistantService) {

    @OnOpen
    suspend fun onOpen(connection: WebSocketConnection): Answer {
        return assistantService.askQuestion(
            memoryId = connection.id(),
            question = "[start]"
        )
            .copy(links = greeting.links)
    }

    @OnTextMessage
    suspend fun onMessage(
        message: String,
        connection: WebSocketConnection
    ): Answer {
        return assistantService.askQuestion(
            memoryId = connection.id(),
            question = message
        )
    }
}

