package io.quarkiverse.langchain4j.chatscopes;

/**
 * Thrown on a client chat invocation when a route is not found.
 */
public class RouteNotFound extends SystemFailure {
    public RouteNotFound() {
        super("Route not found");
    }
}
