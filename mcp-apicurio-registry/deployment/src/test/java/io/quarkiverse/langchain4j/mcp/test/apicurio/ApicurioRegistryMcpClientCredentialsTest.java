package io.quarkiverse.langchain4j.mcp.test.apicurio;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.mcp.runtime.apicurio.ApicurioRegistryMcpTools;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * MCP server discovery authenticates to the Registry with OAuth2 client credentials.
 */
public class ApicurioRegistryMcpClientCredentialsTest extends WiremockAware {

    private static final String SEARCH = "/apis/registry/v3/search/artifacts";
    private static final String AUTHORIZATION = "Bearer t0ken";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.http.test-port", "0")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.apicurio-registry.url",
                    WiremockAware.wiremockUrlForConfig("/apis/registry/v3"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.apicurio-registry.auth.token-endpoint",
                    WiremockAware.wiremockUrlForConfig("/token"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.apicurio-registry.auth.client-id", "agents")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.apicurio-registry.auth.client-secret", "secret");

    @Inject
    ApicurioRegistryMcpTools tools;

    @BeforeEach
    void stubRegistry() {
        resetMappings();
        resetRequests();
        wiremock().register(any(anyUrl()).atPriority(10).willReturn(aResponse().withStatus(401)));
        wiremock().register(post(urlPathEqualTo("/token")).atPriority(1)
                .willReturn(okJson("{\"access_token\":\"t0ken\",\"token_type\":\"Bearer\",\"expires_in\":300}")));
        wiremock().register(get(urlPathEqualTo(SEARCH)).withHeader("Authorization", equalTo(AUTHORIZATION))
                .atPriority(1).willReturn(okJson("{\"count\":0,\"artifacts\":[]}")));
    }

    @Test
    void searchAuthenticatesToTheRegistry() {
        assertThat(tools.searchMcpServers("weather")).isEqualTo("No MCP servers found matching 'weather'");

        wiremock().verifyThat(getRequestedFor(urlPathEqualTo(SEARCH)).withHeader("Authorization", equalTo(AUTHORIZATION)));
    }
}
