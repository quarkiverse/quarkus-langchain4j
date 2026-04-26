package io.quarkiverse.langchain4j.chatscopes.websocket.internal;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.chatscopes.ChatRouteConstants;
import io.quarkiverse.langchain4j.chatscopes.SystemFailure;
import io.quarkiverse.langchain4j.chatscopes.websocket.WebsocketChatRoutes;
import io.quarkiverse.langchain4j.chatscopes.websocket.WebsocketChatRoutes.Client;
import io.quarkiverse.langchain4j.chatscopes.websocket.WebsocketChatRoutes.Session;
import io.quarkiverse.langchain4j.chatscopes.websocket.WebsocketChatRoutes.SessionBuilder;
import io.quarkus.websockets.next.BasicWebSocketConnector;
import io.quarkus.websockets.next.WebSocketClientConnection;
import io.vertx.mutiny.core.Promise;

public class ChatRouteClient implements WebsocketChatRoutes.Client {
    static Logger log = Logger.getLogger(ChatRouteClient.class);

    class JsonEventImpl implements WebsocketChatRoutes.JsonEvent {
        private final JsonNode node;

        public JsonEventImpl(JsonNode node) {
            this.node = node;
        }

        @Override
        public <T> T get(Type type) {
            try {
                return (T) objectMapper.treeToValue(node, objectMapper.constructType(type));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public JsonNode raw() {
            return node;
        }
    }

    class SessionBuilderImpl implements WebsocketChatRoutes.SessionBuilder {
        final String chatId = UUID.randomUUID().toString();
        final Map<String, Consumer<WebsocketChatRoutes.JsonEvent>> eventHandlers = new ConcurrentHashMap<>();
        BiConsumer<String, WebsocketChatRoutes.JsonEvent> defaultHandler;

        @Override
        public SessionBuilder eventHandler(String eventType, Consumer<WebsocketChatRoutes.JsonEvent> handler) {
            eventHandlers.put(eventType, handler);
            return this;
        }

        private void eventStringHandler(String eventType, Consumer<String> handler) {
            eventHandler(eventType, (event) -> handler.accept(event.get(String.class)));
        }

        @Override
        public SessionBuilder messageHandler(Consumer<String> handler) {
            eventStringHandler(ChatRouteConstants.MESSAGE, handler);
            return this;
        }

        @Override
        public SessionBuilder errorHandler(Consumer<String> handler) {
            eventStringHandler(ChatRouteConstants.ERROR, handler);
            return this;
        }

        @Override
        public SessionBuilder consoleHandler(Consumer<String> handler) {
            eventStringHandler(ChatRouteConstants.CONSOLE, handler);
            return this;
        }

        @Override
        public SessionBuilder thinkingHandler(Consumer<String> handler) {
            eventStringHandler(ChatRouteConstants.THINKING, handler);
            return this;
        }

        @Override
        public SessionBuilder streamHandler(Consumer<String> handler) {
            eventStringHandler(ChatRouteConstants.STREAM, handler);
            return this;
        }

        @Override
        public SessionBuilder defaultHandler(BiConsumer<String, WebsocketChatRoutes.JsonEvent> handler) {
            defaultHandler = handler;
            return this;
        }

        @Override
        public Session connect(String initialRoute) {
            return new SessionImpl(this, initialRoute);
        }

        @Override
        public Session connect() {
            return connect(null);
        }
    }

    class SessionImpl implements WebsocketChatRoutes.Session {
        final String chatId;
        final Map<String, Consumer<WebsocketChatRoutes.JsonEvent>> eventHandlers;
        final BiConsumer<String, WebsocketChatRoutes.JsonEvent> defaultHandler;
        volatile Promise<Void> completedPromise;
        volatile String currentScope;

        SessionImpl(SessionBuilderImpl builder, String initialRoute) {
            this.chatId = builder.chatId;
            this.eventHandlers = builder.eventHandlers;
            this.defaultHandler = builder.defaultHandler;
            eventHandlers.put(ChatRouteConstants.COMPLETED, (event) -> completed(event.get(String.class)));
            eventHandlers.put(ChatRouteConstants.FAILED, (event) -> failed(event.get(String.class)));
            sessions.put(chatId, this);
            try {
                String connect = objectMapper.writeValueAsString(new ConnectEvent(chatId, initialRoute));
                Promise<Void> promise = Promise.promise();
                completedPromise = promise;
                connection.sendText(connect).subscribe().with(v -> {
                    // all good, let completed() or failed() handle the rest
                }, e -> {
                    completedPromise = null;
                    promise.fail(e);
                });
                promise.future().ifNoItem().after(Duration.ofSeconds(10))
                        .failWith(new SystemFailure("Connection timeout")).await().indefinitely();
            } catch (Exception e) {
                if (e instanceof SystemFailure) {
                    throw (SystemFailure) e;
                }
                throw new SystemFailure(e);
            }
        }

        void completed(String scopeId) {
            log.debugv("Completed chat request: {0}", scopeId);
            currentScope = scopeId;
            if (completedPromise != null) {
                completedPromise.complete();
            }
        }

        void failed(String error) {
            log.errorf("Failed chat request: %s", error);
            if (completedPromise != null) {
                completedPromise.fail(new SystemFailure(error));
                completedPromise = null;
            }
        }

        static class ChatRouteMessage {
            public final String type = ChatRouteConstants.CHAT_ROUTE_MESSAGE;
            public final String chatId;
            public final String scopeId;
            public final Object data;

            public ChatRouteMessage(String chatId, String scopeId, Object data) {
                this.chatId = chatId;
                this.scopeId = scopeId;
                this.data = data;
            }
        }

        @Override
        public void chat(String userMessage) {
            chat(Map.of("userMessage", userMessage));
        }

        @Override
        public void chat(Object message) {
            chatPromise(message).futureAndAwait();
        }

        @Override
        public Promise<Void> chatPromise(String userMessage) {
            return chatPromise(Map.of("userMessage", userMessage));
        }

        @Override
        public Promise<Void> chatPromise(Object message) {
            ChatRouteMessage chatRouteMessage = new ChatRouteMessage(chatId, currentScope, message);
            try {
                String json = objectMapper.writeValueAsString(chatRouteMessage);
                Promise<Void> promise = Promise.promise();
                completedPromise = promise;
                connection.sendText(json).subscribe().with(v -> {
                    // all good, let completed() or failed() handle the rest
                }, e -> {
                    completedPromise = null;
                    promise.fail(e);
                });
                return promise;
            } catch (Exception e) {
                throw new SystemFailure(e);
            }
        }

        @Override
        public void close() {
            try {
                String json = objectMapper.writeValueAsString(new DisconnectEvent(chatId));
                connection.sendTextAndAwait(json);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    final BasicWebSocketConnector connector;
    final ObjectMapper objectMapper;
    final Map<String, SessionImpl> sessions = new ConcurrentHashMap<>();
    final WebSocketClientConnection connection;

    public ChatRouteClient(BasicWebSocketConnector connector, ObjectMapper objectMapper) {
        this.connector = connector;
        this.objectMapper = objectMapper;
        connector.onTextMessage(this::onTextMessage);
        connection = connector.connectAndAwait();
    }

    @Override
    public WebsocketChatRoutes.SessionBuilder builder() {
        return new SessionBuilderImpl();
    }

    void onTextMessage(WebSocketClientConnection conn, String msg) {
        try {
            log.debugv("Client Received message: {0}", msg);
            JsonNode node = objectMapper.readTree(msg);
            String chatId = node.get("chatId").asText();
            SessionImpl session = sessions.get(chatId);
            if (session == null) {
                log.errorf("Session not found for chatId: %s", chatId);
                return;
            }
            String eventType = node.get("type").asText();
            Consumer<WebsocketChatRoutes.JsonEvent> handler = session.eventHandlers.get(eventType);
            if (handler == null) {
                if (session.defaultHandler != null) {
                    session.defaultHandler.accept(eventType, new JsonEventImpl(node.get("data")));
                } else {
                    log.warnf("Event handler not found for eventType: %s", eventType);
                }
                return;
            }
            WebsocketChatRoutes.JsonEvent event = new JsonEventImpl(node.get("data"));
            handler.accept(event);
        } catch (Exception e) {
            log.error(e);
        }
    }

    static class ConnectEvent {
        public final String type = ChatRouteConstants.CONNECT;
        public final String chatId;
        public final String route;

        public ConnectEvent(String chatId, String route) {
            this.chatId = chatId;
            this.route = route;
        }
    }

    static class DisconnectEvent {
        public final String type = ChatRouteConstants.DISCONNECT;
        public final String chatId;

        public DisconnectEvent(String chatId) {
            this.chatId = chatId;
        }
    }

    public void close() {
        connection.close().await().indefinitely();
    }

}
