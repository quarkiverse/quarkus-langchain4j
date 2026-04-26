package io.quarkiverse.langchain4j.chatscopes;

/**
 * Constants for built in chat route events.
 */
public interface ChatRouteConstants {

    public static final String THINKING = "Thinking";
    public static final String CONSOLE = "Console";
    public static final String MESSAGE = "Message";
    public static final String OBJECT_MESSAGE = "Object";
    public static final String COMPLETED = "Completed";
    public static final String FAILED = "Failed";
    public static final String ROUTE_NOT_FOUND = "RouteNotFound";
    public static final String NO_ROUTE = "NoRoute";
    public static final String SESSION_NOT_ACTIVE = "SessionNotActive";
    public static final String ERROR = "Error";
    public static final String STREAM = "Stream";
    public static final String CONNECT = "Connect";
    public static final String CHAT_ROUTE_MESSAGE = "ChatRouteMessage";
    public static final String DISCONNECT = "Disconnect";
    public static final String CONNECTED = "Connected";
}
