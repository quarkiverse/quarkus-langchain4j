package io.quarkiverse.langchain4j.a2a.runtime.apicurio;

import java.util.Optional;
import java.util.Set;

import io.apicurio.registry.client.common.RegistryClientOptions;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.config.RegistryAuthConfig;
import io.quarkus.runtime.configuration.ConfigurationException;

/**
 * Applies the configured Registry credentials to the client options.
 */
final class RegistryClientAuth {

    private RegistryClientAuth() {
    }

    /**
     * @param options the client options to configure
     * @param auth the configured credentials
     * @param prefix the configuration prefix, for error messages
     * @throws ConfigurationException if more than one mechanism, or an incomplete one, is configured
     */
    static void apply(RegistryClientOptions options, RegistryAuthConfig auth, String prefix) {
        boolean clientCredentials = auth.tokenEndpoint().isPresent() || auth.clientId().isPresent()
                || auth.clientSecret().isPresent();
        boolean basic = auth.username().isPresent() || auth.password().isPresent();
        if (clientCredentials && basic) {
            throw new ConfigurationException("Configure only one way to authenticate to Apicurio Registry: "
                    + prefix + ".auth.client-id (with token-endpoint and client-secret), or " + prefix
                    + ".auth.username (with password)");
        }
        if (clientCredentials) {
            options.oauth2(require(auth.tokenEndpoint(), prefix + ".auth.token-endpoint"),
                    require(auth.clientId(), prefix + ".auth.client-id"),
                    require(auth.clientSecret(), prefix + ".auth.client-secret"),
                    auth.scope().orElse(null));
        } else if (basic) {
            options.basicAuth(require(auth.username(), prefix + ".auth.username"),
                    require(auth.password(), prefix + ".auth.password"));
        }
    }

    private static String require(Optional<String> value, String property) {
        return value.orElseThrow(() -> new ConfigurationException(property + " must be set", Set.of(property)));
    }
}
