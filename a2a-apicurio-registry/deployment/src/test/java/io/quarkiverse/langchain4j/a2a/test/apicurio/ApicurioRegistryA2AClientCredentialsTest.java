package io.quarkiverse.langchain4j.a2a.test.apicurio;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.a2a.runtime.apicurio.ApicurioAgentsRegistry;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Discovery authenticates to the Registry with OAuth2 client credentials.
 */
public class ApicurioRegistryA2AClientCredentialsTest extends WiremockAware {

    private static final String SEARCH = "/apis/registry/v3/search/artifacts";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.http.test-port", "0")
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.apicurio-registry.url",
                    WiremockAware.wiremockUrlForConfig("/apis/registry/v3"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.apicurio-registry.auth.token-endpoint",
                    WiremockAware.wiremockUrlForConfig("/token"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.apicurio-registry.auth.client-id", "agents")
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.apicurio-registry.auth.client-secret", "secret");

    @Inject
    ApicurioAgentsRegistry agentsRegistry;

    @BeforeEach
    void stubRegistry() {
        resetMappings();
        resetRequests();
        wiremock().register(any(anyUrl()).atPriority(10).willReturn(aResponse().withStatus(401)));
        wiremock().register(post(urlPathEqualTo("/token")).atPriority(1)
                .willReturn(okJson("{\"access_token\":\"t0ken\",\"token_type\":\"Bearer\",\"expires_in\":300}")));
        wiremock().register(get(urlPathEqualTo(SEARCH)).withHeader("Authorization", equalTo("Bearer t0ken"))
                .atPriority(1).willReturn(okJson("{\"count\":0,\"artifacts\":[]}")));
    }

    @Test
    void discoverySendsTheTokenFromTheTokenEndpoint() {
        assertThat(agentsRegistry.allAgents()).isEmpty();

        wiremock().verifyThat(postRequestedFor(urlPathEqualTo("/token"))
                .withRequestBody(containing("grant_type=client_credentials"))
                .withRequestBody(containing("client_id=agents")));
        wiremock().verifyThat(getRequestedFor(urlPathEqualTo(SEARCH))
                .withHeader("Authorization", equalTo("Bearer t0ken")));
    }
}
