package io.quarkiverse.langchain4j.mcp.runtime.http;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.client.SseEvent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.mcp.client.transport.McpOperationHandler;

public class SseSubscriber implements Consumer<SseEvent<String>> {

    private final McpOperationHandler operationHandler;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = Logger.getLogger(SseSubscriber.class);
    private final boolean logEvents;
    // this will contain the POST url for sending commands to the server
    private final CompletableFuture<String> initializationFinished;

    public SseSubscriber(
            McpOperationHandler operationHandler,
            boolean logEvents,
            CompletableFuture<String> initializationFinished) {
        this.operationHandler = operationHandler;
        this.logEvents = logEvents;
        this.initializationFinished = initializationFinished;
    }

    @Override
    public void accept(SseEvent<String> s) {
        // some servers send empty messages as pings
        if (s.data().isEmpty() || s.data().isBlank()) {
            return;
        }
        if (logEvents) {
            log.debug("< " + s.data());
        }
        String name = s.name();
        if (name == null) {
            log.warn("Received event with null name");
            return;
        }
        String data = s.data();
        if (name.equals("message")) {
            try {
                JsonNode jsonNode = OBJECT_MAPPER.readTree(data);
                operationHandler.handle(jsonNode);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse JSON message: {}", data, e);
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
