package io.quarkiverse.langchain4j.chatscopes;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.vertx.mutiny.core.Promise;

/**
 * Interfaces for making local chat route invocations.
 */
public interface LocalChatRoutes {

    public interface Session extends AutoCloseable {

        public static final String USER_MESSAGE_KEY = "userMessage";

        /**
         * Invoke current chat route and wait until it is finished
         */
        default void chat() {
            chat(new HashMap<>());
        }

        /**
         * Invoke current chat route and wait until it is finished
         *
         * @param userMessage
         */
        void chat(String userMessage);

        /**
         * Invoke current chat route and wait until it is finished
         * The user message can be sent to the chat route as a {@link #USER_MESSAGE_KEY} key in the message map.
         * The message map maps to the parameter names of the chat route method.
         *
         * @param message
         */
        void chat(Map<String, Object> message);

        /**
         * Invoke current chat route, using a promise to handle the result asynchronously
         *
         * @return
         */
        default Promise<Void> chatPromise() {
            return chatPromise(new HashMap<>());
        }

        /**
         * Invoke current chat route, using a promise to handle the result asynchronously
         *
         * @param message
         */
        Promise<Void> chatPromise(String userMessage);

        /**
         * Invoke current chat route, using a promise to handle the result asynchronously
         * The user message can be sent to the chat route as a {@link #USER_MESSAGE_KEY} key in the message map.
         * The message map maps to the parameter names of the chat route method.
         *
         * @param message
         */
        Promise<Void> chatPromise(Map<String, Object> message);

        void close();
    }

    public interface SessionBuilder {
        /**
         * Register an event handler for a specific event type.
         *
         * @param eventType
         * @param handler
         * @return
         */
        <T> SessionBuilder eventHandler(String eventType, Consumer<T> handler);

        /**
         * Register an event handler for error events.
         * This is shorthand for {@code eventHandler(ChatRouteConstants.ERROR, handler)}
         *
         * @param handler
         * @return
         */
        SessionBuilder errorHandler(Consumer<String> handler);

        /**
         * Register an event handler for message events.
         * This is shorthand for {@code eventHandler(ChatRouteConstants.MESSAGE, handler)}
         *
         * @param handler
         * @return
         */
        SessionBuilder messageHandler(Consumer<String> handler);

        /**
         * Register an event handler for console events.
         * This is shorthand for {@code eventHandler(ChatRouteConstants.CONSOLE, handler)}
         *
         * @param handler
         * @return
         */
        SessionBuilder consoleHandler(Consumer<String> handler);

        /**
         * Register an event handler for thinking events.
         * This is shorthand for {@code eventHandler(ChatRouteConstants.THINKING, handler)}
         *
         * @param handler
         * @return
         */
        SessionBuilder thinkingHandler(Consumer<String> handler);

        /**
         * Register an event handler for stream events. Stream events are used to stream the chat response to the client i.e.
         * when a @ChatRoute method returns a Multi<String>
         * This is shorthand for {@code eventHandler(ChatRouteConstants.STREAM, handler)}
         *
         *
         * @param handler
         * @return
         */
        SessionBuilder streamHandler(Consumer<String> handler);

        /**
         * If there are no event handlers registered for an event type, the default handler will be called.
         * The default handler will be called with the event type and the event data.
         *
         * @param handler
         * @return
         */
        SessionBuilder defaultHandler(BiConsumer<String, Object> handler);

        /**
         * Start a session with the event buss using the initial route as the chat route
         *
         * @param initialRoute
         * @return
         */
        Session connect(String initialRoute);

        /**
         * Start a session with the event buss using the default route as the chat route
         *
         * @return
         */
        Session connect();
    }

    /**
     * A CDI session scope is created per client.
     *
     * @return
     */
    interface Client extends AutoCloseable {

        @Override
        void close();

        SessionBuilder builder();
    }

    Client newClient();

}
