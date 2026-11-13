package io.quarkiverse.langchain4j.mcp.runtime.apicurio;

import java.util.Optional;
import java.util.Set;

import io.apicurio.registry.client.common.RegistryClientOptions;
import io.quarkiverse.langchain4j.mcp.runtime.apicurio.config.RegistryAuthConfig;
import io.quarkus.runtime.configuration.ConfigurationException;

/**
 * Applies the configured Registry credentials to the client options.
 */
final class RegistryClientAuth {

    private RegistryClientAuth() {
    }

    /**
     * Checks the configured credentials. Called at startup, so a misconfiguration fails the
     * application instead of the first registry call.
     *
     * @param auth the configured credentials
     * @param prefix the configuration prefix, for error messages
     * @throws ConfigurationException if more than one mechanism, or an incomplete one, is configured
     */
    static void validate(RegistryAuthConfig auth, String prefix) {
        boolean clientCredentials = auth.tokenEndpoint().isPresent() || auth.clientId().isPresent()
                || auth.clientSecret().isPresent();
        boolean basic = auth.username().isPresent() || auth.password().isPresent();
        if (clientCredentials && basic) {
            throw new ConfigurationException("Configure only one way to authenticate to Apicurio Registry: "
                    + prefix + ".auth.client-id (with token-endpoint and client-secret), or " + prefix
                    + ".auth.username (with password)");
        }
        if (clientCredentials) {
            require(auth.tokenEndpoint(), prefix + ".auth.token-endpoint");
            require(auth.clientId(), prefix + ".auth.client-id");
            require(auth.clientSecret(), prefix + ".auth.client-secret");
        } else if (basic) {
            require(auth.username(), prefix + ".auth.username");
            require(auth.password(), prefix + ".auth.password");
        }
    }

    /**
     * Applies the configured credentials, already checked by {@link #validate}, to the client options.
     */
    static void apply(RegistryClientOptions options, RegistryAuthConfig auth) {
        if (auth.clientId().isPresent()) {
            options.oauth2(auth.tokenEndpoint().get(), auth.clientId().get(), auth.clientSecret().get(),
                    auth.scope().orElse(null));
        } else if (auth.username().isPresent()) {
            options.basicAuth(auth.username().get(), auth.password().get());
        }
    }

    private static String require(Optional<String> value, String property) {
        return value.orElseThrow(() -> new ConfigurationException(property + " must be set", Set.of(property)));
    }
}
