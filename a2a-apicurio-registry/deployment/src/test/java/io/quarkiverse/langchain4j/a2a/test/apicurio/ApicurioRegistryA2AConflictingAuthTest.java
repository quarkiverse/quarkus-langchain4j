package io.quarkiverse.langchain4j.a2a.test.apicurio;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.a2a.runtime.apicurio.ApicurioAgentsRegistry;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Two ways to authenticate at once is a configuration error, not a silent choice.
 */
public class ApicurioRegistryA2AConflictingAuthTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.http.test-port", "0")
            .overrideConfigKey("quarkus.langchain4j.a2a.apicurio-registry.url", "http://localhost:1/apis/registry/v3")
            .overrideConfigKey("quarkus.langchain4j.a2a.apicurio-registry.auth.client-id", "agents")
            .overrideConfigKey("quarkus.langchain4j.a2a.apicurio-registry.auth.client-secret", "secret")
            .overrideConfigKey("quarkus.langchain4j.a2a.apicurio-registry.auth.token-endpoint", "http://localhost:1/token")
            .overrideConfigKey("quarkus.langchain4j.a2a.apicurio-registry.auth.username", "agents")
            .overrideConfigKey("quarkus.langchain4j.a2a.apicurio-registry.auth.password", "secret");

    @Test
    void creatingTheClientFails() {
        assertThatThrownBy(() -> Arc.container().instance(ApicurioAgentsRegistry.class).get().allAgents())
                .hasRootCauseInstanceOf(ConfigurationException.class)
                .rootCause().hasMessageContaining("Configure only one way to authenticate");
    }
}
