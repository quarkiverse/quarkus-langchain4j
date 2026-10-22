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
 * Covers every field the A2A specification marks as REQUIRED on {@code AgentCard}, {@code AgentInterface} and
 * {@code AgentSkill}. A card missing any of them is not a valid discovery document, no matter what the Java API
 * lets us build.
 */
public class RequiredCardFieldsTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(TestAgent.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Weather Agent")
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.description", "Helps with weather");

    @PublicAgentCard
    Instance<AgentCard> agentCardInstance;

    @RegisterAiService
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather in city, or states", tags = "weather"))
    public interface TestAgent {

        String chat(@UserMessage String question);
    }

    @Test
    public void testRequiredCardFields() {
        AgentCard agentCard = agentCardInstance.get();

        assertThat(agentCard.name()).isNotBlank();
        assertThat(agentCard.description()).isNotBlank();
        assertThat(agentCard.version()).isNotBlank();
        assertThat(agentCard.capabilities()).isNotNull();
        assertThat(agentCard.supportedInterfaces()).isNotEmpty();
        assertThat(agentCard.defaultInputModes()).isNotEmpty();
        assertThat(agentCard.defaultOutputModes()).isNotEmpty();
        assertThat(agentCard.skills()).isNotEmpty();
    }

    /**
     * None of the capabilities is implemented by the extension, so an agent that does not ask for them must not
     * advertise them. This is the counterpart of {@link CapabilityFlagsTest}, which pins down the opposite.
     */
    @Test
    public void testNoCapabilityIsAdvertisedUnlessAskedFor() {
        AgentCapabilities capabilities = agentCardInstance.get().capabilities();

        assertThat(capabilities.streaming()).isFalse();
        assertThat(capabilities.pushNotifications()).isFalse();
        assertThat(capabilities.extendedAgentCard()).isFalse();
    }

    /**
     * A single {@code String} in and a single {@code String} out is the only signature the executor supports today,
     * so the card has to advertise plain text on both ends.
     */
    @Test
    public void testModesFollowTheMethodSignature() {
        AgentCard agentCard = agentCardInstance.get();
        assertThat(agentCard.defaultInputModes()).containsExactly("text/plain");
        assertThat(agentCard.defaultOutputModes()).containsExactly("text/plain");
    }
}
