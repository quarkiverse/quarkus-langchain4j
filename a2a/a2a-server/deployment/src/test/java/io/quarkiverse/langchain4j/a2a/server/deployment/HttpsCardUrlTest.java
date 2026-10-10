package io.quarkiverse.langchain4j.a2a.server.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Instance;

import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.certs.Format;
import io.smallrye.certs.junit5.Certificate;
import io.smallrye.certs.junit5.Certificates;

/**
 * An agent reachable over TLS must not hand clients a plaintext address: a card is a discovery document, and one
 * that downgrades the connection undoes the reason for serving over TLS at all.
 */
@Certificates(baseDir = "target/certs", certificates = @Certificate(name = "a2a", password = "password", formats = Format.PKCS12))
public class HttpsCardUrlTest {

    private static final String TLS_PORT = "8449";

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(TestAgent.class))
            .overrideConfigKey("quarkus.tls.key-store.p12.path", "target/certs/a2a-keystore.p12")
            .overrideConfigKey("quarkus.tls.key-store.p12.password", "password")
            // a port of its own, so that pointing the card at the plaintext one is visible in the assertion
            .overrideConfigKey("quarkus.http.test-ssl-port", TLS_PORT)
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Weather Agent");

    @PublicAgentCard
    Instance<AgentCard> agentCardInstance;

    @RegisterAiService
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
    public interface TestAgent {

        String chat(@UserMessage String question);
    }

    @Test
    public void testTheCardAdvertisesHttpsWhenTheServerServesIt() {
        assertThat(agentCardInstance.get().supportedInterfaces())
                .isNotEmpty()
                // the port belongs with the scheme: the TLS scheme on the plaintext port is the same downgrade
                .allSatisfy(agentInterface -> assertThat(agentInterface.url())
                        .startsWith("https://")
                        .endsWith(":" + TLS_PORT));
    }
}
