package io.quarkiverse.langchain4j.workload.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.workload-model-auth-provider")
public interface WorkloadModelAuthProviderBuildConfig {
    /**
     * Whether the Workload Identity Federation ModelAuthProvider should be enabled
     */
    @ConfigDocDefault("true")
    Optional<Boolean> enabled();

    /**
     * The name of the OIDC client to use for the token exchange.
     * If not set, the default OIDC client is used.
     */
    Optional<String> oidcClientName();

    /**
     * The model provider name to bind this model authentication provider to.
     * When set, the provider is qualified with {@link io.quarkiverse.langchain4j.ModelName} and only used
     * by the matching model provider. When not set, the provider is registered as a global default.
     */
    Optional<String> modelName();
}
