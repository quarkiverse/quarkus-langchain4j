package io.quarkiverse.langchain4j.mcp.test.apicurio;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.mcp.runtime.apicurio.ApicurioRegistryMcpTools;
import io.quarkus.test.QuarkusUnitTest;

/**
 * MCP server discovery authenticates to the Registry with OAuth2 client credentials (quarkus-langchain4j#3143).
 */
public class ApicurioRegistryMcpClientCredentialsTest {

    static final String EXPECTED_AUTHORIZATION = "Bearer " + RegistryStub.TOKEN;
    static final int PORT = RegistryStub.start(ApicurioRegistryMcpClientCredentialsTest.class, EXPECTED_AUTHORIZATION);

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(RegistryStub.class))
            .overrideConfigKey("quarkus.http.test-port", "0")
            .overrideConfigKey("quarkus.langchain4j.mcp.apicurio-registry.url", RegistryStub.registryUrl(PORT))
            .overrideConfigKey("quarkus.langchain4j.mcp.apicurio-registry.auth.token-endpoint",
                    RegistryStub.tokenEndpoint(PORT))
            .overrideConfigKey("quarkus.langchain4j.mcp.apicurio-registry.auth.client-id", "agents")
            .overrideConfigKey("quarkus.langchain4j.mcp.apicurio-registry.auth.client-secret", "agents-secret");

    @Inject
    ApicurioRegistryMcpTools tools;

    @Test
    void searchAuthenticatesToTheRegistry() {
        assertThat(tools.searchMcpServers("weather")).isEqualTo("No MCP servers found matching 'weather'");

        RegistryStub.client(PORT)
                .verifyThat(postRequestedFor(urlPathEqualTo("/token"))
                        .withRequestBody(containing("grant_type=client_credentials")));
        RegistryStub.client(PORT)
                .verifyThat(getRequestedFor(urlPathEqualTo(RegistryStub.SEARCH_PATH))
                        .withHeader("Authorization", equalTo(EXPECTED_AUTHORIZATION)));
    }
}
