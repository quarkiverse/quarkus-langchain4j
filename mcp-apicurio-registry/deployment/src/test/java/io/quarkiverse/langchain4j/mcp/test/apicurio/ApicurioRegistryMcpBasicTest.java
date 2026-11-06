package io.quarkiverse.langchain4j.mcp.test.apicurio;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.mcp.runtime.apicurio.ApicurioRegistryMcpTools;
import io.quarkus.test.QuarkusUnitTest;

/**
 * MCP server discovery authenticates to the Registry with HTTP basic authentication (quarkus-langchain4j#3143).
 */
public class ApicurioRegistryMcpBasicTest {

    static final String EXPECTED_AUTHORIZATION = "Basic "
            + Base64.getEncoder().encodeToString("agents:agents-secret".getBytes(StandardCharsets.UTF_8));
    static final int PORT = RegistryStub.start(ApicurioRegistryMcpBasicTest.class, EXPECTED_AUTHORIZATION);

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(RegistryStub.class))
            .overrideConfigKey("quarkus.http.test-port", "0")
            .overrideConfigKey("quarkus.langchain4j.mcp.apicurio-registry.url", RegistryStub.registryUrl(PORT))
            .overrideConfigKey("quarkus.langchain4j.mcp.apicurio-registry.auth.username", "agents")
            .overrideConfigKey("quarkus.langchain4j.mcp.apicurio-registry.auth.password", "agents-secret");

    @Inject
    ApicurioRegistryMcpTools tools;

    @Test
    void searchAuthenticatesToTheRegistry() {
        assertThat(tools.searchMcpServers("weather")).isEqualTo("No MCP servers found matching 'weather'");

        RegistryStub.client(PORT)
                .verifyThat(getRequestedFor(urlPathEqualTo(RegistryStub.SEARCH_PATH))
                        .withHeader("Authorization", equalTo(EXPECTED_AUTHORIZATION)));
    }
}
