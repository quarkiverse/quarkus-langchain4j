package io.quarkiverse.langchain4j.sample.apicurio;

import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;

@WebSocket(path = "/chatbot")
public class ChatBotWebSocket {

    private final Bot bot;

    public ChatBotWebSocket(Bot bot) {
        this.bot = bot;
    }

    @OnOpen
    public String onOpen() {
        return "Hello! I can search Apicurio Registry for MCP servers and use their tools. What would you like to do?";
    }

    @OnTextMessage
    public String onMessage(String message) {
        return bot.chat(message);
    }
}
