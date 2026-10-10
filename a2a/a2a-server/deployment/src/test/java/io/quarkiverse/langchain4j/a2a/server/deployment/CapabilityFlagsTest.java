package io.quarkiverse.langchain4j.a2a.server.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Instance;

import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * The three capability flags are advertisements: they tell clients what to expect of the agent, and the extension
 * carries them from the annotation onto the card without implementing anything itself. What has to hold is that a
 * flag set on the annotation is the flag clients read, since a card claiming a capability the agent does not have
 * is worse than one claiming nothing.
 */
public class CapabilityFlagsTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(TestAgent.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Weather Agent");

    @PublicAgentCard
    Instance<AgentCard> agentCardInstance;

    @RegisterAiService
    @ExposeA2AAgent(streaming = true, pushNotifications = true, extendedAgentCard = true, skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
    public interface TestAgent {

        String chat(@UserMessage String question);
    }

    @Test
    public void testEveryFlagReachesTheCard() {
        AgentCapabilities capabilities = agentCardInstance.get().capabilities();

        assertThat(capabilities.streaming()).isTrue();
        assertThat(capabilities.pushNotifications()).isTrue();
        assertThat(capabilities.extendedAgentCard()).isTrue();
    }
}
