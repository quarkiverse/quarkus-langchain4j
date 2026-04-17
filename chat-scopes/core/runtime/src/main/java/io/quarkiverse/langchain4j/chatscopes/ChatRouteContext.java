package io.quarkiverse.langchain4j.chatscopes;

import java.lang.reflect.Type;

/**
 * This interface is used on the server to represent a chat route invocation.
 */
public interface ChatRouteContext {

    public interface Request {
        /**
         * Shorthand for {@code data("userMessage", String.class)}
         *
         * @return
         */
        String userMessage();

        /**
         * Get a data value from the request.
         *
         * @param <T>
         * @param key
         * @param type
         * @return
         */
        <T> T data(String key, Type type);
    }

    public interface ResponseChannel {
        /**
         * Send an event to the client.
         *
         * @param eventType
         * @param data
         */
        void event(String eventType, Object data);

        /**
         * Send an event to the client. The event type will be determined by the data class.
         * The data class must have a @EventType annotation so the event type can be determined.
         *
         * @param data
         */
        default void event(Object data) {
            if (data == null) {
                throw new IllegalArgumentException("event argument cannot be null");
            }
            EventType eventType = data.getClass().getAnnotation(EventType.class);
            if (eventType != null) {
                event(eventType.value(), data);
            } else {
                throw new IllegalArgumentException("Unable to determine event type. " + data.getClass().getName()
                        + " does not have a @EventType annotation");
            }
        }

        /**
         * Convenience method for sending a built in error event type. Sends a string error message to the client
         * This is shorthand for {@code event(ChatRouteConstants.ERROR, msg)}
         *
         * @param msg
         */
        default void error(String msg) {
            event(ChatRouteConstants.ERROR, msg);
        }

        /**
         * Convenience method for sending a built in thinking event type. Sends a string thinking message to the client
         * This is shorthand for {@code event(ChatRouteConstants.THINKING, msg)}
         *
         * @param msg
         */
        default void thinking(String msg) {
            event(ChatRouteConstants.THINKING, msg);
        }

        /**
         * Send an object message to the client.
         * This is shorthand for {@code event(ChatRouteConstants.OBJECT_MESSAGE, data)}
         *
         * @param data
         */
        default void objectMessage(Object data) {
            event(ChatRouteConstants.OBJECT_MESSAGE, data);
        }

        /**
         * Convenience method for sending a built in console event type. Sends a string console message to the client
         * This is shorthand for {@code event(ChatRouteConstants.CONSOLE, msg)}
         *
         * @param msg
         */
        default void console(String msg) {
            event(ChatRouteConstants.CONSOLE, msg);
        }

        /**
         * Convenience method for sending a built in message event type. Sends a string message to the client
         * This is shorthand for {@code event(ChatRouteConstants.MESSAGE, msg)}
         *
         * @param msg
         */
        default void message(String msg) {
            event(ChatRouteConstants.MESSAGE, msg);
        }
    }

    Request request();

    ResponseChannel response();

}
