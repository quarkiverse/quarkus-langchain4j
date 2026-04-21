package io.quarkiverse.langchain4j.chatscopes.websocket.internal;

import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.chatscopes.ChatRouteConstants;
import io.quarkiverse.langchain4j.chatscopes.ChatRouteContext;
import io.quarkiverse.langchain4j.chatscopes.RouteNotFound;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatRouter;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatScopeManagedContext;
import io.quarkiverse.langchain4j.chatscopes.internal.ServerChatRouteContext;
import io.quarkus.websockets.next.*;

@SessionScoped
@WebSocket(path = "/_chat/routes")
public class ChatRouteEndpoint {
    static Logger log = Logger.getLogger(ChatRouteEndpoint.class);

    @Inject
    WebSocketConnection connection;

    @Inject
    ObjectMapper objectMapper;

    ChatRouter router = new ChatRouter();

    public record Event(String chatId, String type, Object data) {
    }

    ConcurrentHashMap<String, String> sessions = new ConcurrentHashMap<>();

    private void sendEvent(WebSocketConnection conn, String chatId, String event, Object data) {
        try {
            String json = objectMapper.writeValueAsString(new Event(chatId, event, data));
            conn.sendTextAndAwait(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void sendEvent(String chatId, String event, Object data) {
        sendEvent(connection, chatId, event, data);
    }

    public class ChatRouteContextImpl implements ServerChatRouteContext {
        public String chatId;
        public ResponseImpl response;
        public RequestImpl request;

        public ChatRouteContextImpl(String chatId, JsonNode state) {
            this.chatId = chatId;
            this.request = new RequestImpl(chatId, new JsonState(objectMapper, state));
            this.response = new ResponseImpl();
        }

        @Override
        public Request request() {
            return request;
        }

        @Override
        public ServerResponseChannel response() {
            return response;
        }

        public record RequestImpl(String chatId, JsonState state) implements ChatRouteContext.Request {
            @Override
            public String userMessage() {
                return state.get("userMessage", String.class);
            }

            @Override
            public <T> T data(String key, Type type) {
                return state.get(key, type);
            }
        }

        public class ResponseImpl implements ServerResponseChannel {
            @Override
            public void event(String event, Object data) {
                sendEvent(chatId, event, data);
            }

            @Override
            public void stream(String packet) {
                event(ChatRouteConstants.STREAM, packet);
            }

            @Override
            public void completed(String scopeId) {
                event(ChatRouteConstants.COMPLETED, scopeId);
            }

            @Override
            public void failed(String error) {
                event(ChatRouteConstants.FAILED, error);
            }

        }
    }

    @OnTextMessage
    @ActivateRequestContext
    public void onMessage(String text) {
        try {
            log.debugv("   {0}", text);
            JsonNode msg = objectMapper.readTree(text);
            String type = msg.get("type").asText();
            String chatId = msg.get("chatId").asText();
            if (type.equals(ChatRouteConstants.CONNECT)) {
                JsonNode routeNode = msg.get("route");
                String route = routeNode != null ? routeNode.asText() : null;
                String scopeId = null;
                try {
                    scopeId = router.connect(route);
                } catch (RouteNotFound e) {
                    sendEvent(chatId, ChatRouteConstants.FAILED, e.getMessage());
                    return;
                }
                sessions.put(chatId, scopeId);
                log.debugv("Connected to scope: {0}", scopeId);
                sendEvent(chatId, ChatRouteConstants.COMPLETED, scopeId);
            } else if (type.equals(ChatRouteConstants.CHAT_ROUTE_MESSAGE)) {
                String scopeId = msg.get("scopeId").asText();
                JsonNode data = msg.get("data");
                ChatRouteContextImpl ctx = new ChatRouteContextImpl(chatId, data);
                router.execute(scopeId, ctx);
            } else if (type.equals(ChatRouteConstants.DISCONNECT)) {
                String scopeId = sessions.remove(chatId);
                if (scopeId != null) {
                    ChatScopeManagedContext.INSTANCE.destroy(scopeId);
                }
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        }

    }

    @PreDestroy
    public void preDestroy() {
        log.debugv("Destroying chat route endpoint");
        sessions.values().forEach(ChatScopeManagedContext.INSTANCE::destroy);
    }

}
