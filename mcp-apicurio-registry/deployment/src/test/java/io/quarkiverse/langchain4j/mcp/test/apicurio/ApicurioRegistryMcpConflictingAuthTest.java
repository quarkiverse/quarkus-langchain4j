package io.quarkiverse.langchain4j.mcp.test.apicurio;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.mcp.runtime.apicurio.ApicurioRegistryMcpTools;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Two ways to authenticate at once is a configuration error, not a silent choice.
 */
public class ApicurioRegistryMcpConflictingAuthTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.http.test-port", "0")
            .overrideConfigKey("quarkus.langchain4j.mcp.apicurio-registry.url", "http://localhost:1/apis/registry/v3")
            .overrideConfigKey("quarkus.langchain4j.mcp.apicurio-registry.auth.client-id", "agents")
            .overrideConfigKey("quarkus.langchain4j.mcp.apicurio-registry.auth.client-secret", "secret")
            .overrideConfigKey("quarkus.langchain4j.mcp.apicurio-registry.auth.token-endpoint", "http://localhost:1/token")
            .overrideConfigKey("quarkus.langchain4j.mcp.apicurio-registry.auth.username", "agents")
            .overrideConfigKey("quarkus.langchain4j.mcp.apicurio-registry.auth.password", "secret");

    @Test
    void creatingTheToolsFails() {
        assertThatThrownBy(() -> Arc.container().instance(ApicurioRegistryMcpTools.class).get().searchMcpServers("x"))
                .hasRootCauseInstanceOf(ConfigurationException.class)
                .rootCause().hasMessageContaining("Configure only one way to authenticate");
    }
}
