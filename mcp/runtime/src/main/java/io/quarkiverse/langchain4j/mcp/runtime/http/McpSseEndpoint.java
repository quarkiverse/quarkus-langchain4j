package io.quarkiverse.langchain4j.mcp.runtime.http;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.client.SseEvent;

import io.smallrye.mutiny.Multi;

public interface McpSseEndpoint {

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    Multi<SseEvent<String>> get();
}
