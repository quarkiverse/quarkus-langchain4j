package io.quarkiverse.langchain4j.sample.assistant.resource;

import io.quarkiverse.langchain4j.sample.assistant.dto.McpConnection;
import io.quarkiverse.langchain4j.sample.assistant.service.McpConnectionRequest;
import io.quarkiverse.langchain4j.sample.assistant.service.McpConnectionService;
import io.quarkus.logging.Log;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/mcp-connections")
public class McpConnectionResource {

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance connectionList(List<McpConnection> connections);
        public static native TemplateInstance formFeedback(String error);
    }

    @Inject
    McpConnectionService mcpConnectionService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance listConnections() {
        List<McpConnection> connections = mcpConnectionService.listConnections();
        return Templates.connectionList(connections);
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance addConnection(@FormParam("name") String name,
                                          @FormParam("transportType") String transportType,
                                          @FormParam("command") String command,
                                          @FormParam("url") String url) {
        try {
            McpConnectionRequest request = new McpConnectionRequest(
                name, McpConnectionRequest.TransportType.valueOf(transportType),
                command,
                url
            );
            mcpConnectionService.addConnection(request);
            return Templates.formFeedback(null);
        } catch (Exception e) {
            Log.error("Failed adding a MCP connection: " + e);
            return Templates.formFeedback(e.getMessage());
        }
    }

    @DELETE
    @Path("/{name}")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance removeConnection(@PathParam("name") String name) {
        mcpConnectionService.removeConnection(name);
        List<McpConnection> connections = mcpConnectionService.listConnections();
        return Templates.connectionList(connections);
    }
}
