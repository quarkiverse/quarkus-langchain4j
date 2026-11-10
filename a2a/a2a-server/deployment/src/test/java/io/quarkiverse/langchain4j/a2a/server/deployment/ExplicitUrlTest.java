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

/**
 * An agent behind a proxy or an ingress is reached at an address it never sees itself, so the configured URL has to
 * win over the one the server is bound to. This is the escape hatch every other case falls back on, and the only
 * one that survives the agent moving.
 */
public class ExplicitUrlTest {

    private static final String PUBLIC_URL = "https://agents.example.com/weather";

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(TestAgent.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Weather Agent")
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.url", PUBLIC_URL);

    @PublicAgentCard
    Instance<AgentCard> agentCardInstance;

    @RegisterAiService
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
    public interface TestAgent {

        String chat(@UserMessage String question);
    }

    @Test
    public void testTheConfiguredUrlIsWhatEveryInterfaceAdvertises() {
        assertThat(agentCardInstance.get().supportedInterfaces())
                .isNotEmpty()
                .allSatisfy(agentInterface -> assertThat(agentInterface.url()).isEqualTo(PUBLIC_URL));
    }
}
