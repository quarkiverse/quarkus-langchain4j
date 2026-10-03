package io.quarkiverse.langchain4j.a2a.server.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Instance;

import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * An AI Service whose signature goes beyond a single {@code String} in and a {@code String} out: it takes a memory
 * id alongside the message and returns a record.
 */
public class StructuredResultAgentTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(TestAgent.class, Forecast.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Weather Agent");

    @PublicAgentCard
    Instance<AgentCard> agentCardInstance;

    public record Forecast(String city, int temperature) {
    }

    @RegisterAiService
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "forecast", name = "Forecast", description = "Structured forecast", tags = "weather"))
    public interface TestAgent {

        Forecast chat(@MemoryId String conversationId, @UserMessage String question);
    }

    /**
     * The message is still plain text, but a record result is structured data, so the card must advertise JSON on
     * the way out only.
     */
    @Test
    public void testModesReflectTheStructuredResult() {
        AgentCard agentCard = agentCardInstance.get();
        assertThat(agentCard.defaultInputModes()).containsExactly("text/plain");
        assertThat(agentCard.defaultOutputModes()).containsExactly("application/json");
    }
}
