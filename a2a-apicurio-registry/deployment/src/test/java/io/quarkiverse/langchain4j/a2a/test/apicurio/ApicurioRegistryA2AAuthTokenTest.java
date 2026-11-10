package io.quarkiverse.langchain4j.a2a.test.apicurio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * auth-token was never applied, so a configured token was silently ignored. It now fails startup and
 * names the settings to use instead.
 */
public class ApicurioRegistryA2AAuthTokenTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.http.test-port", "0")
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.apicurio-registry.url", "http://localhost:1/apis/registry/v3")
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.apicurio-registry.auth-token", "static-token")
            .assertException(e -> assertThat(e).isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("quarkus.langchain4j.a2a.apicurio-registry.auth-token is not supported")
                    .hasMessageContaining("auth.client-id"));

    @Test
    void startupFails() {
        fail("Startup should have failed");
    }
}
