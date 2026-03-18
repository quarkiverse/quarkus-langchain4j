package io.quarkiverse.langchain4j.chatscopes.websocket;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.chatscopes.websocket.internal.ChatRouteClient;
import io.quarkus.arc.Arc;
import io.quarkus.websockets.next.BasicWebSocketConnector;
import io.vertx.mutiny.core.Promise;

/**
 * Interfaces for making websocket chat route invocations.
 */
public interface WebsocketChatRoutes {
    /**
     * Sessions are not thread-safe.
     *
     * Sessions represent a single chat conversation with the server.
     */
    public interface Session extends AutoCloseable {
        /**
         * Invoke current chat route and wait until it is finished.
         * The current chat route of the chat scope conversation will be invoked on the server.
         */
        default void chat() {
            chat(new HashMap<>());
        }

        /**
         * Invoke current chat route and wait until it is finished
         * The current chat route of the chat scope conversation will be invoked on the server.
         *
         * @param userMessage
         */
        void chat(String userMessage);

        /**
         * Invoke current chat route and wait until it is finished
         * The message parameter is marshalled to JSON and sent to the server.
         * The top level JSON properties of the message object are used as the parameters of the chat route method.
         *
         * The current chat route of the chat scope conversation will be invoked on the server.
         *
         * @param message
         */
        void chat(Object message);

        /**
         * Invoke current chat route using a Promise to handle the result asynchronously
         *
         * The current chat route of the chat scope conversation will be invoked on the server.
         *
         * @return
         */
        default Promise<Void> chatPromise() {
            return chatPromise(new HashMap<>());
        }

        /**
         * Invoke current chat route using a Promise to handle the result asynchronously
         *
         * The current chat route of the chat scope conversation will be invoked on the server.
         *
         * @param userMessage
         * @return
         */
        Promise<Void> chatPromise(String userMessage);

        /**
         * Invoke current chat route using a Promise to handle the result asynchronously
         * The message parameter is marshalled to JSON and sent to the server.
         * The top level JSON properties of the message object are used as the parameters of the chat route method.
         *
         * The current chat route of the chat scope conversation will be invoked on the server.
         *
         * @param message
         * @return
         */
        Promise<Void> chatPromise(Object message);

        /**
         * Closes the chat session which will clean up any CDI chat scoped beans associated with this session.
         */
        void close();
    }

    /**
     * Events are sent back as json to the client. This interface is used
     * to get at the raw jason data, or to deserialize it into a specific type.
     */
    interface JsonEvent {
        /**
         * Deserialize the event from JSON into a specific type.
         *
         * @param <T>
         * @param type
         * @return
         */
        <T> T get(Type type);

        /**
         * Get the raw JSON for the event.
         *
         * @return
         */
        JsonNode raw();
    }

    public interface SessionBuilder {
        /**
         * Register an event handler for a specific event type.
         *
         * @param eventType
         * @param handler
         * @return
         */
        SessionBuilder eventHandler(String eventType, Consumer<JsonEvent> handler);

        /**
         * Register an event handler for message events. This is shorthand for
         * {@code eventHandler(ChatRouteConstants.MESSAGE, handler)}
         *
         * @param handler
         * @return
         */
        SessionBuilder messageHandler(Consumer<String> handler);

        /**
         * Register an event handler for error events. This is shorthand for
         * {@code eventHandler(ChatRouteConstants.ERROR, handler)}
         *
         * @param handler
         * @return
         */
        SessionBuilder errorHandler(Consumer<String> handler);

        /**
         * Register an event handler for console events. This is shorthand for
         * {@code eventHandler(ChatRouteConstants.CONSOLE, handler)}
         *
         * @param handler
         * @return
         */
        SessionBuilder consoleHandler(Consumer<String> handler);

        /**
         * Register an event handler for thinking events. This is shorthand for
         * {@code eventHandler(ChatRouteConstants.THINKING, handler)}
         *
         * @param handler
         * @return
         */
        SessionBuilder thinkingHandler(Consumer<String> handler);

        /**
         * Register an event handler for stream events. This is shorthand for
         * {@code eventHandler(ChatRouteConstants.STREAM, handler)}
         * Each stream event is a chunk sent by the server, i.e. if a chat route returns a Multi<String>
         *
         * @param handler
         * @return
         */
        SessionBuilder streamHandler(Consumer<String> handler);

        /**
         * Register an event handler for default events. This is called if no event handler is registered for the event type.
         *
         * @param handler
         * @return
         */
        SessionBuilder defaultHandler(BiConsumer<String, JsonEvent> handler);

        /**
         * Connect to the server using the initial route.
         *
         * @param initialRoute
         * @return
         */
        Session connect(String initialRoute);

        /**
         * Connect to the server using the default route.
         *
         * @return
         */
        Session connect();
    }

    /**
     * Represents a single websocket connection to the server.
     */
    interface Client extends AutoCloseable {
        /**
         * Closes the websocket connection to the server.
         */
        @Override
        void close();

        SessionBuilder builder();
    }

    static Client newClient(BasicWebSocketConnector connector, ObjectMapper objectMapper) {
        return new ChatRouteClient(connector, objectMapper);
    }

    /**
     * Create a new client using CDI to look up the client's ObjectMapper.
     *
     * @param connector
     * @return
     */
    static Client newClient(BasicWebSocketConnector connector) {
        return newClient(connector, Arc.container().instance(ObjectMapper.class).get());
    }
}
