package io.quarkiverse.langchain4j.sample.chatbot

import io.quarkus.websockets.next.OnOpen
import io.quarkus.websockets.next.OnTextMessage
import io.quarkus.websockets.next.WebSocket

@WebSocket(path = "/chatbot")
@Suppress("unused")
class ChatBotWebSocket(private val bot: Bot) {
    @OnOpen
    fun onOpen(): String = "Hello, I'm Bob, how can I help you?"

    @OnTextMessage
    fun onMessage(message: String): String {
        return bot.chat(message)
    }
}
