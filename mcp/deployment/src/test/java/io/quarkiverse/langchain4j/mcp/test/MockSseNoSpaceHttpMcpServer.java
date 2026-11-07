package io.quarkiverse.langchain4j.mcp.test;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A mock MCP server using the streamable HTTP transport that wraps every JSON-RPC
 * response in an SSE stream whose {@code data} field has no space after the colon
 * ({@code data:{...}} instead of {@code data: {...}}). Both forms are valid according
 * to the SSE specification and real servers such as Spring AI's emit the no-space variant.
 */
@Path("/mock-sse-no-space-mcp")
public class MockSseNoSpaceHttpMcpServer extends AbstractMockHttpMcpServer {

    @Override
    @Path("/mcp")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @POST
    public Response post(@HeaderParam("Authorization") String authorization, JsonNode message) {
        Response response = super.post(authorization, message);
        if (response.getStatus() == 200 && response.getEntity() instanceof JsonNode json) {
            // no space after the colons, ids and event names are also emitted in the no-space form
            String sse = "id:" + json.get("id").asText() + "\n"
                    + "event:message\n"
                    + "data:" + json + "\n\n";
            return Response.ok(sse, MediaType.SERVER_SENT_EVENTS).build();
        }
        return response;
    }

    @Override
    protected String getToolsListResponse() {
        return MockHttpMcpServer.TOOLS_LIST_RESPONSE;
    }

    @Override
    protected String getEndpoint() {
        return "mock-sse-no-space-mcp";
    }
}
