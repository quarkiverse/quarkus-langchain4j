package io.quarkiverse.langchain4j.a2a.test.apicurio;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.a2a.runtime.apicurio.ApicurioAgentsRegistry;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusUnitTest;

/**
 * auth-token was never applied, so a configured token was silently ignored
 * (quarkus-langchain4j#3142). It now fails and names the settings to use instead.
 */
public class ApicurioRegistryA2AAuthTokenTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.http.test-port", "0")
            .overrideConfigKey("quarkus.langchain4j.a2a.apicurio-registry.url", "http://localhost:1/apis/registry/v3")
            .overrideConfigKey("quarkus.langchain4j.a2a.apicurio-registry.auth-token", "static-token");

    @Test
    void aConfiguredTokenIsReportedInsteadOfIgnored() {
        assertThatThrownBy(() -> Arc.container().instance(ApicurioAgentsRegistry.class).get().allAgents())
                .hasRootCauseInstanceOf(ConfigurationException.class)
                .rootCause()
                .hasMessageContaining("quarkus.langchain4j.a2a.apicurio-registry.auth-token is not supported")
                .hasMessageContaining("auth.client-id");
    }
}
