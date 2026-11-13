package io.quarkiverse.langchain4j.mcp.test.apicurio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Two ways to authenticate at once fails startup.
 */
public class ApicurioRegistryMcpConflictingAuthTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.http.test-port", "0")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.apicurio-registry.url", "http://localhost:1/apis/registry/v3")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.apicurio-registry.auth.client-id", "agents")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.apicurio-registry.auth.client-secret", "secret")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.apicurio-registry.auth.token-endpoint",
                    "http://localhost:1/token")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.apicurio-registry.auth.username", "agents")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.apicurio-registry.auth.password", "secret")
            .assertException(e -> assertThat(e).isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("Configure only one way to authenticate"));

    @Test
    void startupFails() {
        fail("Startup should have failed");
    }
}
