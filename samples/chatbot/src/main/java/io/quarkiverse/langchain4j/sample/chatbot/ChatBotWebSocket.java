package io.quarkiverse.langchain4j.sample.chatbot;

import java.io.IOException;

import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;

import io.smallrye.mutiny.infrastructure.Infrastructure;

@ServerEndpoint("/chatbot")
public class ChatBotWebSocket {

    @Inject
    Bot bot;

    @Inject
    ChatMemoryBean chatMemoryBean;

    @OnOpen
    public void onOpen(Session session) {
        Infrastructure.getDefaultExecutor().execute(() -> {
            String response = bot.chat(session, "hello");
            try {
                session.getBasicRemote().sendText(response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @OnClose
    void onClose(Session session) {
        chatMemoryBean.clear(session);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        Infrastructure.getDefaultExecutor().execute(() -> {
            String response = bot.chat(session, message);
            try {
                session.getBasicRemote().sendText(response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

    }

}
