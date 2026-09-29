package io.quarkiverse.langchain4j.prompt.runtime.apicurio.config;

import java.time.Duration;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.prompt.apicurio-registry")
public interface PromptApicurioRegistryRuntimeConfig {

    /**
     * The base URL of the Apicurio Registry instance
     * (e.g. http://localhost:8080/apis/registry/v3).
     */
    Optional<String> url();

    /**
     * The group used for prompt templates referenced without an explicit {@code groupId}.
     */
    @WithDefault("default")
    String defaultGroup();

    /**
     * The version expression used for prompt templates referenced without an explicit {@code version}.
     * <p>
     * Accepts anything the registry accepts as a version expression: a version number (e.g. {@code 1.0.0}) or a
     * branch reference (e.g. {@code branch=latest}). The alias {@code latest} is mapped to {@code branch=latest}.
     */
    @WithDefault("branch=latest")
    String defaultVersion();

    /**
     * How long a resolved prompt template is considered fresh.
     * <p>
     * Once the TTL expires, the next use of the template keeps rendering the cached content while a refresh from the
     * registry happens in the background, so AI service invocations are never blocked by the registry after the
     * first resolution. When the refresh fails, the cached content is kept and a warning is logged.
     * <p>
     * Set to {@code 0} (or a negative value) to never refresh templates: they are fetched once and cached for the
     * lifetime of the application.
     */
    @WithDefault("60s")
    Duration cacheTtl();

    /**
     * Whether every prompt template referenced by an AI service ({@code @SystemMessageFromRegistry} /
     * {@code @UserMessageFromRegistry}) is fetched and validated when the application starts.
     * <p>
     * When enabled, the application fails to start if a template cannot be resolved or if it declares required
     * variables that are not bound by the AI service method using it. When disabled, templates are resolved lazily
     * on first use.
     */
    @WithDefault("true")
    boolean validateOnStartup();

    /**
     * HTTP basic authentication settings.
     */
    BasicAuthConfig basicAuth();

    /**
     * OAuth2 client credentials settings.
     */
    OAuth2Config oauth2();

    interface BasicAuthConfig {

        /**
         * The username to use to authenticate with the registry.
         */
        Optional<String> username();

        /**
         * The password to use to authenticate with the registry.
         */
        Optional<String> password();
    }

    interface OAuth2Config {

        /**
         * The OAuth2 token endpoint used to obtain access tokens with the client credentials flow.
         */
        Optional<String> tokenEndpoint();

        /**
         * The OAuth2 client id.
         */
        Optional<String> clientId();

        /**
         * The OAuth2 client secret.
         */
        Optional<String> clientSecret();

        /**
         * The OAuth2 scope to request.
         */
        Optional<String> scope();
    }
}
