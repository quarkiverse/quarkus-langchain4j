package io.quarkiverse.langchain4j.mcp.runtime.http;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.client.SseEvent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.mcp.client.transport.http.SseEventListener;

public class SseSubscriber implements Consumer<SseEvent<String>> {

    private final Map<Long, CompletableFuture<JsonNode>> pendingOperations;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = Logger.getLogger(SseEventListener.class);
    private final boolean logEvents;
    // this will contain the POST url for sending commands to the server
    private final CompletableFuture<String> initializationFinished;

    public SseSubscriber(
            Map<Long, CompletableFuture<JsonNode>> pendingOperations,
            boolean logEvents,
            CompletableFuture<String> initializationFinished) {
        this.pendingOperations = pendingOperations;
        this.logEvents = logEvents;
        this.initializationFinished = initializationFinished;
    }

    @Override
    public void accept(SseEvent<String> s) {
        if (logEvents) {
            log.debug("< " + s.data());
        }
        String name = s.name();
        if (name == null) {
            log.warn("Received event with null name");
            return;
        }
        if (name.equals("message")) {
            try {
                JsonNode message = OBJECT_MAPPER.readValue(s.data(), JsonNode.class);
                long messageId = message.get("id").asLong();
                CompletableFuture<JsonNode> op = pendingOperations.remove(messageId);
                if (op != null) {
                    op.complete(message);
                } else {
                    log.warn("Received response for unknown message id: " + messageId);
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse response data", e);
            }
        } else if (name.equals("endpoint")) {
            if (initializationFinished.isDone()) {
                log.warn("Received endpoint event after initialization");
                return;
            }
            initializationFinished.complete(s.data());
        }
    }
}
