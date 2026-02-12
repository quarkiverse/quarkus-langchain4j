package io.quarkiverse.langchain4j.sample.assistant.oidc;

import io.quarkiverse.langchain4j.sample.assistant.resource.McpConnectionResource.Templates;
import io.quarkiverse.langchain4j.sample.assistant.service.McpConnectionRequest;
import io.quarkiverse.langchain4j.sample.assistant.service.McpConnectionService;
import io.quarkus.logging.Log;
import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.oidc.OidcSession;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Authenticated
@Path("/mcp-authorization")
public class McpAuthorizationResource {


    @Inject
    McpConnectionService mcpConnectionService;
    
    @Inject
    OidcSession oidcSession;

    @Inject 
    AccessTokenCredential accessToken;
        
    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response addConnection(@QueryParam("name") String name,
                                  @QueryParam("transportType") String transportType,
                                  @QueryParam("command") String command,
                                  @QueryParam("url") String url) {
        try {
            McpConnectionRequest request = new McpConnectionRequest(
                name, McpConnectionRequest.TransportType.valueOf(transportType),
                command,
                url,
                accessToken.getToken()
            );
            mcpConnectionService.addConnection(request);
            return Response.ok(Templates.formFeedback(null)).build();
        } catch (Exception e) {
            Log.error("Failed adding a MCP connection: " + e);
            return Response.ok(Templates.formFeedback(e.getMessage())).build();
        }
    }
}
