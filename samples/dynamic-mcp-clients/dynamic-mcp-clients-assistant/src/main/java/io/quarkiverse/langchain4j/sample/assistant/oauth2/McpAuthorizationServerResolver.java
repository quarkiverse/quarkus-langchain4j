package io.quarkiverse.langchain4j.sample.assistant.oauth2;

import java.net.URI;
import java.util.List;
import java.util.function.Function;

import io.quarkus.logging.Log;
import io.quarkus.oidc.OidcRequestContext;
import io.quarkus.oidc.OidcTenantConfig;
import io.quarkus.oidc.OidcTenantConfigBuilder;
import io.quarkus.oidc.TenantConfigResolver;
import io.quarkus.oidc.runtime.OidcTenantConfig.ApplicationType;
import io.quarkus.oidc.runtime.builders.AuthenticationConfigBuilder;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class McpAuthorizationServerResolver implements TenantConfigResolver {

    WebClient webClient;
    
    @Inject
    public McpAuthorizationServerResolver(Vertx vertx) {
        this.webClient = WebClient.create(vertx); 
    }
    
    @Override
    public Uni<OidcTenantConfig> resolve(RoutingContext routingContext,
            OidcRequestContext<OidcTenantConfig> requestContext) {
        final List<String> urlParam = routingContext.queryParam("url");
        if (urlParam == null || urlParam.isEmpty()) {
            return null;
        }
        final String tenantId = getTenantId(routingContext);
        final String resourceMetadataUrl = getProtectedResourceMetadataUrl(routingContext, tenantId, urlParam.get(0));
        
        return webClient.getAbs(resourceMetadataUrl).send()
                .onItem().transform(new Function<HttpResponse<Buffer>, OidcTenantConfig>() {

                    @Override
                    public OidcTenantConfig apply(HttpResponse<Buffer> resourceMetadataResponse) {
                        
                        if (resourceMetadataResponse.statusCode() == 404) {
                            Log.warnf("Protected resource metadata endpoint for the %s MCP server does not exist at %s", 
                                    tenantId, resourceMetadataUrl);
                            return null;
                        }
                        
                        final JsonObject metadata = resourceMetadataResponse.bodyAsJsonObject();
                        final String resource = metadata.getString("resource");
                        
                        String authorizationServer = null;
                        final JsonArray authorizationServers = metadata.getJsonArray("authorization_servers");
                        if (authorizationServers != null && !authorizationServers.isEmpty()) {
                            authorizationServer = authorizationServers.getString(0);
                        }
                        if (authorizationServer == null) {
                            Log.warnf("Protected resource metadata for the %s MCP server does not link to an authorization server", 
                                    tenantId);
                            return null;
                        }
                        
                        Log.infof("%s MCP server metadata: resource - %s, authorization server - %s", 
                                tenantId, resource, authorizationServer);
                        
                        
                        //TODO: currently it depends on the authorization server supporting an OIDC discovery mechanism
                        // which is only one of the options listed in the MCP Authorization section for discovering
                        // the server metadata
                        
                        OidcTenantConfigBuilder builder = OidcTenantConfig.builder()
                                .tenantId(tenantId)
                                .authServerUrl(authorizationServer)
                                .applicationType(ApplicationType.WEB_APP)
                                .clientId(getClientId(routingContext));
                        
                        
                        final List<String> scopes = getScopes(routingContext);
                                               
                        if (resource != null || !scopes.isEmpty()) {
                            AuthenticationConfigBuilder authBuilder =  builder.authentication();
                            if (resource != null) {
                                // MCP Authorization expects that this resource will end up as the token audience value
                                authBuilder.extraParam("resource", resource);
                            }
                                               
                            if (!scopes.isEmpty()) {
                                // If the authorization server does not recognize a `resource` indicator as per
                                // https://www.rfc-editor.org/rfc/rfc8707, the scopes can help to set a specific audience
                                authBuilder.scopes(scopes);
                            }
                            authBuilder.end();
                        }
                        
                        return builder.build();
                    }
                    
                });
    }

    /**
     * Gets the protected resource metadata URL. Uses the resource_metadata query parameter
     * if provided, otherwise constructs it from the MCP server URL.
     */
    static String getProtectedResourceMetadataUrl(RoutingContext routingContext, String tenantId, String mcpServerUrl) {
        // Check if resource_metadata was provided as a query parameter
        List<String> resourceMetadataParam = routingContext.queryParam("resource_metadata");
        if (resourceMetadataParam != null && !resourceMetadataParam.isEmpty() 
            && resourceMetadataParam.get(0) != null && !resourceMetadataParam.get(0).trim().isEmpty()) {
            
            Log.infof("Using %s as a protected resource metadata endpoint address for the %s MCP server", resourceMetadataParam.get(0),
                    tenantId);
            return resourceMetadataParam.get(0);
        }
        Log.warnf("%s MCP server is not compliant with the MCP authorization specification because it did not"
                + " return a WWW-Authenticate resource_metadata parameter that points"
                + " to its protected resource metadata endpoint", getTenantId(routingContext));
        // Fallback: construct the URL from the MCP server URL
        // This fallback should never be executed since the MCP authorization spec insists that
        // "MCP servers MUST use the HTTP header WWW-Authenticate when returning a 401 Unauthorized
        // to indicate the location of the resource server metadata URL"
        // i.e, resourceMetadataParam check above must succeed for compliant MCP servers
        
        URI mcpUri = URI.create(mcpServerUrl);
        String protectedResourceMetadataUri = mcpUri.getScheme() + "://" + mcpUri.getAuthority()
            + "/.well-known/oauth-protected-resource";
        
        Log.infof("Trying to use %s as a protected resource metadata endpoint address for the %s MCP server", protectedResourceMetadataUri,
                tenantId);
        
        return protectedResourceMetadataUri;
    }

    static String getTenantId(RoutingContext routingContext) {
        
        List<String> queryParam = routingContext.queryParam("name");
        if (queryParam == null || queryParam.isEmpty()) {
            // Not expected to happen
            return null;
        }
        return queryParam.get(0);
    }
    
    static String getClientId(RoutingContext routingContext) {
        List<String> queryParam = routingContext.queryParam("client_id");
        if (queryParam == null || queryParam.isEmpty()) {
            return "default-client";
        }
        return queryParam.get(0);
    }
    
    static List<String> getScopes(RoutingContext routingContext) {
        List<String> queryParam = routingContext.queryParam("scopes");
        if (queryParam == null || queryParam.isEmpty()) {
            return List.of();
        }
        String scopesString = queryParam.get(0);
        if (scopesString == null || scopesString.trim().isEmpty()) {
            return List.of();
        }
        return List.of(scopesString.trim().split("\\s+"));
    }
}
