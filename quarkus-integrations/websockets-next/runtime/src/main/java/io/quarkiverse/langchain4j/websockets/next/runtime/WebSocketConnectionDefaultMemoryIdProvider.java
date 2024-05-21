package io.quarkiverse.langchain4j.websockets.next.runtime;

import jakarta.enterprise.context.ContextNotActiveException;

import io.quarkiverse.langchain4j.spi.DefaultMemoryIdProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.websockets.next.WebSocketConnection;

/**
 * This implementation uses the current WebSocket connection ID as the default value
 */
public class WebSocketConnectionDefaultMemoryIdProvider implements DefaultMemoryIdProvider {

    @Override
    public Object getMemoryId() {
        ArcContainer container = Arc.container();
        if (container == null) {
            return null;
        }
        InstanceHandle<WebSocketConnection> instance = container.instance(WebSocketConnection.class);
        if (instance.isAvailable()) {
            try {
                return instance.get().id();
            } catch (ContextNotActiveException ignored) {
                // this means that the session scope was not active, so we can't provide a value
                return null;
            }
        }
        return null;
    }
}
