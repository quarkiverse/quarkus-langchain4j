package org.acme;

import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.smallrye.mutiny.Multi;

@WebSocket(path = "/chatbot")
public class ChatBotWebSocket {

    private final ChatBotService chatBotService;

    public ChatBotWebSocket(ChatBotService chatBotService) {
        this.chatBotService = chatBotService;
    }

    @OnOpen
    public String onOpen() {
        return "How can I help you today?";
    }

    @OnTextMessage
    public Multi<String> onTextMessage(String message) {
        return chatBotService.chat(message);
    }
}
