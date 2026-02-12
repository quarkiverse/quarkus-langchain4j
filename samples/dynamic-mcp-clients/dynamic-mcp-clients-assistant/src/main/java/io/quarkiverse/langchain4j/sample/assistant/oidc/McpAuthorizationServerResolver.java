package io.quarkiverse.langchain4j.sample.assistant.oidc;

import java.net.URI;
import java.util.List;
import java.util.function.Function;

import io.quarkus.oidc.OidcRequestContext;
import io.quarkus.oidc.OidcTenantConfig;
import io.quarkus.oidc.OidcTenantConfigBuilder;
import io.quarkus.oidc.TenantConfigResolver;
import io.quarkus.oidc.runtime.OidcTenantConfig.ApplicationType;
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
        List<String> urlParam = routingContext.queryParam("url");
        if (urlParam == null || urlParam.isEmpty()) {
            return null;
        }
        URI mcpServerUrl = URI.create(urlParam.get(0));
        String resourceMetadataUrl = 
                mcpServerUrl.getScheme() + "://" + mcpServerUrl.getAuthority() + "/.well-known/oauth-protected-resource";
        
        return webClient.getAbs(resourceMetadataUrl).send()
                .onItem().transform(new Function<HttpResponse<Buffer>, OidcTenantConfig>() {

                    @Override
                    public OidcTenantConfig apply(HttpResponse<Buffer> resourceMetadataResponse) {
                        
                        JsonObject metadata = resourceMetadataResponse.bodyAsJsonObject();
                        JsonArray authorizationServers = metadata.getJsonArray("authorization_servers");
                        String authorizationServer = authorizationServers.getString(0);
                  
                        List<String> scopes = getScopes(routingContext);
                        OidcTenantConfigBuilder builder = OidcTenantConfig.builder()
                                .tenantId(getTenantId(routingContext))
                                .authServerUrl(authorizationServer)
                                .applicationType(ApplicationType.WEB_APP)
                                .clientId(getClientId(routingContext));
                        
                        if (!scopes.isEmpty()) {
                            builder.authentication().scopes(scopes).end();
                        }
                        
                        return builder.build();
                    }
                    
                });
    }

    static String getTenantId(RoutingContext routingContext) {
        
        List<String> queryParam = routingContext.queryParam("name");
        if (queryParam == null || queryParam.isEmpty()) {
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
