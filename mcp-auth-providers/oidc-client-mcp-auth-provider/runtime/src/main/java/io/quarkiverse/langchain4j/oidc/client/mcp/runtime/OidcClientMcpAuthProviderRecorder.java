package io.quarkiverse.langchain4j.oidc.client.mcp.runtime;

import java.util.function.Function;

import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpRuntimeConfiguration;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClients;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.ConfigurationException;

@Recorder
public class OidcClientMcpAuthProviderRecorder {

    private static final String OIDC_CLIENT_NAME = "oidc-client-name";

    private final RuntimeValue<McpRuntimeConfiguration> mcpRuntimeConfiguration;

    public OidcClientMcpAuthProviderRecorder(RuntimeValue<McpRuntimeConfiguration> mcpRuntimeConfiguration) {
        this.mcpRuntimeConfiguration = mcpRuntimeConfiguration;
    }

    public Function<SyntheticCreationalContext<McpClientAuthProvider>, McpClientAuthProvider> provider(
            String mcpClientName, String deprecatedOidcClientName) {
        return context -> {
            String oidcClientName = mcpRuntimeConfiguration.getValue().clients().get(mcpClientName)
                    .extraParams().get(OIDC_CLIENT_NAME);
            if (oidcClientName == null) {
                oidcClientName = deprecatedOidcClientName;
            }
            OidcClients oidcClients = context.getInjectedReference(OidcClients.class);
            OidcClient oidcClient;
            if (oidcClientName != null) {
                oidcClient = oidcClients.getClient(oidcClientName);
                if (oidcClient == null) {
                    throw new ConfigurationException(
                            "OIDC client '" + oidcClientName + "' not found for MCP client '" + mcpClientName + "'");
                }
            } else {
                oidcClient = oidcClients.getClient();
            }
            return new OidcClientMcpAuthProvider(oidcClient);
        };
    }
}
