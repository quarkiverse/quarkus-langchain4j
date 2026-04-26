package io.quarkiverse.langchain4j.sample.assistant.resource;

import java.util.List;
import java.util.concurrent.ExecutionException;

import io.quarkiverse.langchain4j.mcp.auth.McpAuthenticationException;
import io.quarkiverse.langchain4j.sample.assistant.dto.McpConnection;
import io.quarkiverse.langchain4j.sample.assistant.service.McpConnectionRequest;
import io.quarkiverse.langchain4j.sample.assistant.service.McpConnectionService;
import io.quarkus.logging.Log;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

@Path("/api/mcp-connections")
public class McpConnectionResource {

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance connectionList(List<McpConnection> connections);
        public static native TemplateInstance formFeedback(String error);
        public static native TemplateInstance authRequired(String name, String transportType, String command, String url, String resourceMetadata);
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
                                  @FormParam("url") String url,
                                  @Context UriInfo uriInfo) {
        try {
            McpConnectionRequest request = new McpConnectionRequest(
                name, McpConnectionRequest.TransportType.valueOf(transportType),
                command,
                url
            );
            mcpConnectionService.addConnection(request);
            return Templates.formFeedback(null);
        } catch (Exception e) {
            if (e.getCause() instanceof ExecutionException && 
                    e.getCause().getCause() instanceof McpAuthenticationException authEx) {
                Log.infof("%s MCP server requires authentication", name);
                return Templates.authRequired(name, transportType, command, url, authEx.getResourceMetadata());
            } else {
                Log.error("Failed adding a MCP connection: " + e);
                return Templates.formFeedback(e.getMessage());
            }
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
