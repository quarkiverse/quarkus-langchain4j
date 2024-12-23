package io.quarkiverse.langchain4j.mcp.runtime.http;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import dev.langchain4j.mcp.client.protocol.McpClientMessage;
import io.smallrye.mutiny.Uni;

public interface McpPostEndpoint {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Response> post(McpClientMessage message);

}
