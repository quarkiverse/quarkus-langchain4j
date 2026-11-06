package io.quarkiverse.langchain4j.a2a.test.apicurio;

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

import io.quarkiverse.langchain4j.a2a.runtime.apicurio.ApicurioAgentsRegistry;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Discovery authenticates to the Registry with OAuth2 client credentials (quarkus-langchain4j#3142).
 */
public class ApicurioRegistryA2AClientCredentialsTest {

    static final int PORT = RegistryStub.start(ApicurioRegistryA2AClientCredentialsTest.class, "Bearer " + RegistryStub.TOKEN);

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(RegistryStub.class))
            .overrideConfigKey("quarkus.http.test-port", "0")
            .overrideConfigKey("quarkus.langchain4j.a2a.apicurio-registry.url", RegistryStub.registryUrl(PORT))
            .overrideConfigKey("quarkus.langchain4j.a2a.apicurio-registry.auth.token-endpoint",
                    RegistryStub.tokenEndpoint(PORT))
            .overrideConfigKey("quarkus.langchain4j.a2a.apicurio-registry.auth.client-id", "agents")
            .overrideConfigKey("quarkus.langchain4j.a2a.apicurio-registry.auth.client-secret", "agents-secret");

    @Inject
    ApicurioAgentsRegistry agentsRegistry;

    @Test
    void discoverySendsTheTokenFromTheTokenEndpoint() {
        assertThat(agentsRegistry.allAgents()).isEmpty();

        RegistryStub.client(PORT)
                .verifyThat(postRequestedFor(urlPathEqualTo("/token"))
                        .withRequestBody(containing("grant_type=client_credentials"))
                        .withRequestBody(containing("client_id=agents")));
        RegistryStub.client(PORT)
                .verifyThat(getRequestedFor(urlPathEqualTo(RegistryStub.SEARCH_PATH))
                        .withHeader("Authorization", equalTo("Bearer " + RegistryStub.TOKEN)));
    }
}
