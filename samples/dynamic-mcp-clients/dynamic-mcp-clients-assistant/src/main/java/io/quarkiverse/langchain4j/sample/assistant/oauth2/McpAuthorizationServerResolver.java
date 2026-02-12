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
        
        Uni<McpServerProtectedMetadata> mcpServerProtectedMetadataUni = webClient.getAbs(resourceMetadataUrl).send()
                .onItem().transform(new Function<HttpResponse<Buffer>, McpServerProtectedMetadata>() {

                    @Override
                    public McpServerProtectedMetadata apply(HttpResponse<Buffer> resourceMetadataResponse) {
                        
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
                        
                        return new McpServerProtectedMetadata(resource, authorizationServer);
                    }
                    
                });
        
        Uni<McpServerAuthorizatonServer> mcpServerAuthorizationServerUni = findDiscoveryPath(mcpServerProtectedMetadataUni); 
                 
        
        return mcpServerAuthorizationServerUni.onItem().transform(new Function<McpServerAuthorizatonServer, OidcTenantConfig>() {

            @Override
            public OidcTenantConfig apply(McpServerAuthorizatonServer server) {

                if (server == null) {
                    // LOG warning must'be already been issued by now.
                    return null;
                }
                
                if (!server.authorizationServerDiscoveryPath.endsWith("/.well-known/openid-configuration")) {
                    Log.infof("Currently, a secured MCP server can only be imported if its authorization server's"
                            + " metadata endpoint URL path ends with '/.well-known/openid-configuration'");
                    return null;
                }
                
                Log.infof("Creating OidcTenantConfig, authServerUrl: %s, discoveryPath: %s", 
                        server.authorizationServerBaseUri(), server.authorizationServerDiscoveryPath());
                
                OidcTenantConfigBuilder builder = OidcTenantConfig.builder()
                        .tenantId(tenantId)
                        .authServerUrl(server.authorizationServerBaseUri())
                        //.discoveryPath(server.authorizationServerDiscoveryPath())
                        .applicationType(ApplicationType.WEB_APP)
                        .clientId(getClientId(routingContext));
                
                String clientSecret = getClientSecret(routingContext);
                if (clientSecret != null && !clientSecret.isEmpty()) {
                    builder.credentials(clientSecret);
                }
                
                
                AuthenticationConfigBuilder authBuilder =  builder.authentication();
                
                authBuilder.idTokenRequired(false);
                //authBuilder.userInfoRequired(false);
                
                if (server.resource() != null) {
                    // MCP Authorization expects that this resource will end up as the token audience value
                    authBuilder.extraParam("resource", server.resource());
                }
                
                final List<String> scopes = getScopes(routingContext);
                if (!scopes.isEmpty()) {
                    // If the authorization server does not recognize a `resource` indicator as per
                    // https://www.rfc-editor.org/rfc/rfc8707, the scopes can help to set a specific audience
                    authBuilder.scopes(scopes);
                }
                authBuilder.end();
                
                return builder.build();
            }
            
        });
    }

    private Uni<McpServerAuthorizatonServer> findDiscoveryPath(
            Uni<McpServerProtectedMetadata> mcpServerProtectedMetadataUni) {
        return mcpServerProtectedMetadataUni.onItem().transformToUni(
                new Function<McpServerProtectedMetadata, Uni<? extends McpServerAuthorizatonServer>>() {

                   @Override
                   public Uni<McpServerAuthorizatonServer> apply(McpServerProtectedMetadata metadata) {
                       if (metadata == null) {
                           return Uni.createFrom().nullItem();
                       }
                       
                       String authServerUrl = metadata.authorizationServer();
                       URI authUri = URI.create(authServerUrl);
                       String path = authUri.getPath();
                       
                       // Try discovery endpoints in priority order according to MCP spec
                       if (path != null && !path.isEmpty() && !"/".equals(path)) {
                           // For issuer URLs with path components
                           String pathComponent = path.startsWith("/") ? path.substring(1) : path;
                           String baseUri = authUri.getScheme() + "://" + authUri.getAuthority();
                           
                           return tryDiscoveryEndpoints(metadata.resource(), baseUri, path, List.of(
                               path + "/.well-known/openid-configuration",    
                               "/.well-known/oauth-authorization-server/" + pathComponent,
                               "/.well-known/openid-configuration/" + pathComponent
                           ));
                       } else {
                           // For issuer URLs without path components
                           String baseUri = authUri.getScheme() + "://" + authUri.getAuthority();
                           
                           return tryDiscoveryEndpoints(metadata.resource(), baseUri, null, List.of(
                               "/.well-known/openid-configuration",
                               "/.well-known/oauth-authorization-server"
                           ));
                       }
                   }
                    
                });
    }
    
    private Uni<McpServerAuthorizatonServer> tryDiscoveryEndpoints(String resource, String baseUri, String path, List<String> discoveryPaths) {
        return tryDiscoveryEndpoint(resource, baseUri, path, discoveryPaths, 0);
    }
    
    private Uni<McpServerAuthorizatonServer> tryDiscoveryEndpoint(String resource, String baseUri, String path, List<String> discoveryPaths, int index) {
        if (index >= discoveryPaths.size()) {
            Log.warnf("Failed to discover authorization server metadata at any of the standard endpoints for %s", baseUri);
            return Uni.createFrom().nullItem();
        }
        
        final String discoveryPath = discoveryPaths.get(index);
        final String discoveryUrl = baseUri + discoveryPath;
        
        Log.infof("Trying authorization server metadata discovery at: %s", discoveryUrl);
        
        return webClient.headAbs(discoveryUrl).send()
            .onItem().transformToUni(response -> {
                if (response.statusCode() == 200) {
                    Log.infof("Successfully discovered authorization server metadata at: %s", discoveryUrl);
                    
                    String authServerBaseUri = baseUri;
                    String authServerDiscoveryPath = discoveryPath;
                    if (path != null && authServerDiscoveryPath.startsWith(path)) {
                        authServerBaseUri += path;
                        authServerDiscoveryPath = authServerDiscoveryPath.substring(path.length());
                    }
                    
                    return Uni.createFrom().item(new McpServerAuthorizatonServer(resource, authServerBaseUri, authServerDiscoveryPath));
                } else {
                    Log.debugf("Discovery endpoint %s returned status %d, trying next endpoint", discoveryUrl, response.statusCode());
                    return tryDiscoveryEndpoint(resource, baseUri, path, discoveryPaths, index + 1);
                }
            })
            .onFailure().recoverWithUni(throwable -> {
                Log.debugf("Failed to reach discovery endpoint %s: %s, trying next endpoint", discoveryUrl, throwable.getMessage());
                return tryDiscoveryEndpoint(resource, baseUri, path, discoveryPaths, index + 1);
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
    
    static String getClientSecret(RoutingContext routingContext) {
        List<String> queryParam = routingContext.queryParam("client_secret");
        if (queryParam == null || queryParam.isEmpty()) {
            return null;
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
    
    static record McpServerProtectedMetadata(String resource, String authorizationServer) {
        
    }
    
    static record McpServerAuthorizatonServer(String resource, String authorizationServerBaseUri, String authorizationServerDiscoveryPath) {
        
    }
}
