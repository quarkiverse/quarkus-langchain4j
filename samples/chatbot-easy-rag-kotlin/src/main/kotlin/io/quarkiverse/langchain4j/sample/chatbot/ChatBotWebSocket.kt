package io.quarkiverse.langchain4j.sample.chatbot

import io.quarkus.logging.Log
import io.quarkus.websockets.next.OnError
import io.quarkus.websockets.next.OnOpen
import io.quarkus.websockets.next.OnTextMessage
import io.quarkus.websockets.next.WebSocket
import io.quarkus.websockets.next.WebSocketConnection
import jakarta.enterprise.context.SessionScoped
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.serialization.Serializable

@WebSocket(path = "/chatbot")
@Suppress("unused")
@SessionScoped
class ChatBotWebSocket(private val assistantService: AssistantService) {

    @OnOpen
    fun onOpen(connection: WebSocketConnection): Answer {
        return greeting
    }

    @OnTextMessage
    suspend fun onMessage(
        request: Request,
        connection: WebSocketConnection
    ): Answer {
        val userTimezone = FixedOffsetTimeZone(offset = UtcOffset(minutes = -request.timezoneOffset))
        val userInfo = mapOf("timeZone" to userTimezone.id)

        return assistantService.askQuestion(
            memoryId = request.sessionId,
            question = request.message,
            userInfo = userInfo,
        )
    }

    @OnError
    fun onError(connection: WebSocketConnection, error: Throwable): Answer {
        Log.error("Error while processing message", error)
        return errorAnswer
    }

    @Serializable
    data class Request(val message: String, val sessionId: String, val timezoneOffset: Int)
}

