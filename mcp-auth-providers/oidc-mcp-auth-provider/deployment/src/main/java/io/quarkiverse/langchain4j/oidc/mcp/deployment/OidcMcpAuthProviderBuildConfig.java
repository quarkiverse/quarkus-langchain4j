package io.quarkiverse.langchain4j.oidc.mcp.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.oidc-mcp-auth-provider")
public interface OidcMcpAuthProviderBuildConfig {
    /**
     * Whether the OIDC McpClientAuthProvider should be enabled
     */
    @ConfigDocDefault("true")
    Optional<Boolean> enabled();
}
