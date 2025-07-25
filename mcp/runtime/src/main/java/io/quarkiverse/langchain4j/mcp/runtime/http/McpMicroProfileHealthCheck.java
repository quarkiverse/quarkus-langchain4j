package io.quarkiverse.langchain4j.mcp.runtime.http;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

public interface McpMicroProfileHealthCheck {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    String healthCheck();
}
