package io.quarkiverse.langchain4j.sample.chatbot;

import java.io.IOException;

import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;

import org.eclipse.microprofile.context.ManagedExecutor;

import io.quarkiverse.langchain4j.ChatMemoryRemover;

@ServerEndpoint("/chatbot")
public class ChatBotWebSocket {

    @Inject
    Bot bot;

    @Inject
    ManagedExecutor managedExecutor;

    @OnOpen
    public void onOpen(Session session) {
        managedExecutor.execute(() -> {
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
        ChatMemoryRemover.remove(bot, session);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        managedExecutor.execute(() -> {
            String response = bot.chat(session, message);
            try {
                session.getBasicRemote().sendText(response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

    }

}
