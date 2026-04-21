package io.quarkiverse.langchain4j.chatscopes.internal;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.chatscopes.ChatRouteConstants;
import io.quarkiverse.langchain4j.chatscopes.ChatRouteContext;
import io.quarkiverse.langchain4j.chatscopes.LocalChatRoutes;
import io.quarkiverse.langchain4j.chatscopes.SystemFailure;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableContext.ContextState;
import io.quarkus.arc.ManagedContext;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.common.annotation.Blocking;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.core.eventbus.EventBus;

@ApplicationScoped
@Default
public class ChatRouteEventBus implements LocalChatRoutes {

    static Logger log = Logger.getLogger(ChatRouteEventBus.class);

    record LocalChatRouteRequest(String chatId, String scopeId, Map<String, Object> data) {
    }

    record LocalChatRouteEvent(String chatId, String type, Object data) {
    }

    @Inject
    EventBus eventBus;

    ChatRouter router = new ChatRouter();
    Map<String, SessionImpl> sessions = new ConcurrentHashMap<>();

    @Override
    public LocalChatRoutes.Client newClient() {
        return new ClientImpl();
    }

    @Produces
    @Dependent
    public LocalChatRoutes.Client produceClient() {
        return newClient();
    }

    public void destroyClient(@Disposes LocalChatRoutes.Client client) {
        client.close();
    }

    class ChatRouteContextImpl implements ServerChatRouteContext {
        public LocalChatRouteRequest localRequest;
        public ResponseImpl response;
        public RequestImpl request;

        public ChatRouteContextImpl(LocalChatRouteRequest localRequest) {
            this.localRequest = localRequest;
            this.response = new ResponseImpl();
            this.request = new RequestImpl(localRequest);
        }

        @Override
        public Request request() {
            return request;
        }

        @Override
        public ServerResponseChannel response() {
            return response;
        }

        public record RequestImpl(LocalChatRouteRequest localRequest) implements ChatRouteContext.Request {
            @Override
            public String userMessage() {
                return (String) localRequest.data.get("userMessage");
            }

            @Override
            public <T> T data(String key, Type type) {
                return (T) localRequest.data.get(key);
            }
        }

        public class ResponseImpl implements ServerResponseChannel {
            @Override
            public void event(String event, Object data) {
                sendEvent(localRequest.chatId, event, data);
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
            public void failed(String msg) {
                event(ChatRouteConstants.FAILED, msg);
            }
        }
    }

    private void sendEvent(String chatId, String event, Object data) {
        eventBus.send("chat-route-event", new LocalChatRouteEvent(chatId, event, data));
    }

    @ConsumeEvent("chat-route-request")
    @Blocking
    public void serverRequest(LocalChatRouteRequest request) {
        ChatRouteContextImpl ctx = new ChatRouteContextImpl(request);
        log.debugv("Received server request: {0} {1} {2}", request.chatId, request.scopeId, ctx.request().userMessage());
        SessionImpl session = sessions.get(request.chatId);
        if (session == null) {
            ctx.response().failed(ChatRouteConstants.SESSION_NOT_ACTIVE);
            log.warnf("Session not found for chatId: %s", request.chatId);
            return;
        }
        session.client.sessionContext.activate(session.client.sessionState);
        try {
            router.execute(request.scopeId, ctx);
            log.debugv("Completed server request: {0}", request.chatId);
        } finally {
            session.client.sessionContext.deactivate();
        }
    }

    @ConsumeEvent("chat-route-event")
    public void onEvent(LocalChatRouteEvent event) {
        log.debugv("Received client event: {0}, {1}", event.chatId, event.type);
        SessionImpl session = sessions.get(event.chatId);
        if (session == null) {
            log.warnf("Session not found for chatId: %s", event.chatId);
            return;
        }
        Consumer consumer = session.eventHandlers.get(event.type);
        if (consumer != null) {
            try {
                consumer.accept(event.data);
            } catch (Exception e) {
                log.error("Error handling event", e);
            }
        } else if (session.defaultHandler != null) {
            try {
                session.defaultHandler.accept(event.type, event.data);
            } catch (Exception e) {
                log.error("Error handling event", e);
            }
        } else {
        }
    }

    class ClientImpl implements LocalChatRoutes.Client {
        final ContextState sessionState = Arc.container().sessionContext().initializeState();
        final ManagedContext sessionContext = Arc.container().sessionContext();
        final ConcurrentHashMap<String, SessionImpl> sessions = new ConcurrentHashMap<>();

        @Override
        public LocalChatRoutes.SessionBuilder builder() {
            return new SessionBuilderImpl(this);
        }

        @Override
        public void close() {
            sessions.values().forEach(SessionImpl::clientClose);
        }
    }

    class SessionBuilderImpl implements LocalChatRoutes.SessionBuilder {
        final ClientImpl client;
        final String chatId = UUID.randomUUID().toString();
        final Map<String, Consumer> eventHandlers = new ConcurrentHashMap<>();
        BiConsumer<String, Object> defaultHandler;

        SessionBuilderImpl(ClientImpl client) {
            this.client = client;
        }

        @Override
        public <T> SessionBuilder eventHandler(String eventType, Consumer<T> handler) {
            eventHandlers.put(eventType, handler);
            return this;
        }

        @Override
        public SessionBuilder errorHandler(Consumer<String> handler) {
            eventHandler(ChatRouteConstants.ERROR, handler);
            return this;
        }

        @Override
        public SessionBuilder messageHandler(Consumer<String> handler) {
            eventHandler(ChatRouteConstants.MESSAGE, handler);
            return this;
        }

        @Override
        public SessionBuilder consoleHandler(Consumer<String> handler) {
            eventHandler(ChatRouteConstants.CONSOLE, handler);
            return this;
        }

        @Override
        public SessionBuilder thinkingHandler(Consumer<String> handler) {
            eventHandler(ChatRouteConstants.THINKING, handler);
            return this;
        }

        @Override
        public SessionBuilder streamHandler(Consumer<String> handler) {
            eventHandler(ChatRouteConstants.STREAM, handler);
            return this;
        }

        @Override
        public SessionBuilder defaultHandler(BiConsumer<String, Object> handler) {
            this.defaultHandler = handler;
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

    class SessionImpl implements LocalChatRoutes.Session {
        final ClientImpl client;
        final String chatId;
        final String topScope;
        volatile String currentScope;
        volatile Promise<Void> completedPromise;

        final Map<String, Consumer> eventHandlers;
        final BiConsumer<String, Object> defaultHandler;

        SessionImpl(SessionBuilderImpl builder, String initialRoute) {
            this.client = builder.client;
            this.chatId = builder.chatId;
            this.eventHandlers = builder.eventHandlers;
            this.defaultHandler = builder.defaultHandler;
            eventHandlers.put(ChatRouteConstants.COMPLETED, (Consumer<String>) this::completed);
            eventHandlers.put(ChatRouteConstants.FAILED, (Consumer<String>) this::failed);
            this.topScope = this.currentScope = router.connect(initialRoute);
            sessions.put(chatId, this);
            client.sessions.put(chatId, this);
        }

        void completed(String scopeId) {
            currentScope = scopeId;
            log.debugv("Completed chat request: {0}", scopeId);
            if (completedPromise != null) {
                completedPromise.complete();
            }
        }

        void failed(String msg) {
            log.errorf("Failed chat request: %s", msg);
            if (completedPromise != null) {
                completedPromise.fail(new SystemFailure(msg));
            }
        }

        @Override
        public void chat(String userMessage) {
            chat(Map.of("userMessage", userMessage));
        }

        @Override
        public void chat(Map<String, Object> data) {
            chatPromise(data).futureAndAwait();
        }

        @Override
        public Promise<Void> chatPromise(String userMessage) {
            return chatPromise(Map.of("userMessage", userMessage));
        }

        @Override
        public Promise<Void> chatPromise(Map<String, Object> data) {
            Promise<Void> promise = Promise.promise();
            completedPromise = promise;
            eventBus.send("chat-route-request", new LocalChatRouteRequest(chatId, currentScope, data));
            return promise;
        }

        void clientClose() {
            sessions.remove(chatId);
            ChatScopeManagedContext.INSTANCE.destroy(topScope);
        }

        @Override
        public void close() {
            client.sessions.remove(chatId);
            clientClose();
        }
    }

}
