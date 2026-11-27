package io.quarkiverse.langchain4j.a2a.runtime.apicurio.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

/**
 * Credentials for the Apicurio Registry API. Configure at most one mechanism: OAuth2 client
 * credentials (token endpoint, client ID and secret) or HTTP basic authentication.
 */
@ConfigGroup
public interface RegistryAuthConfig {

    /**
     * The OAuth2 token endpoint, for the client credentials flow
     * (e.g. https://keycloak.example.com/realms/registry/protocol/openid-connect/token).
     * Tokens are fetched and refreshed automatically.
     */
    Optional<String> tokenEndpoint();

    /**
     * The OAuth2 client ID, for the client credentials flow.
     */
    Optional<String> clientId();

    /**
     * The OAuth2 client secret, for the client credentials flow.
     */
    Optional<String> clientSecret();

    /**
     * The OAuth2 scope to request, for the client credentials flow.
     */
    Optional<String> scope();

    /**
     * The user name, for HTTP basic authentication to the Apicurio Registry API, which must accept it (for example with
     * apicurio.authn.basic-client-credentials.enabled).
     */
    Optional<String> username();

    /**
     * The password, for HTTP basic authentication to the Apicurio Registry API, which must accept it (for example with
     * apicurio.authn.basic-client-credentials.enabled).
     */
    Optional<String> password();
}
