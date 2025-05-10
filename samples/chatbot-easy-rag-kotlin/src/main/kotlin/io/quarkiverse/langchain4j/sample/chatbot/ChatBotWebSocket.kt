package io.quarkiverse.langchain4j.sample.chatbot

import io.quarkus.websockets.next.OnOpen
import io.quarkus.websockets.next.OnTextMessage
import io.quarkus.websockets.next.WebSocket

@WebSocket(path = "/chatbot")
@Suppress("unused")
class ChatBotWebSocket(private val bot: Bot) {
    @OnOpen
    fun onOpen(): Answer = Answer(
        message = "Hello, I'm Bob, how can I help you?",
        links = listOf(
            Link("https://www.example.com/help", "Help(example.com)")
        )
    )

    @OnTextMessage
    fun onMessage(message: String): Answer {
        return bot.chat(message)
    }
}

