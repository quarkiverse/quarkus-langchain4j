package io.quarkiverse.langchain4j.a2a.server.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.AgentCardBuilderCustomizer;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Customizers are applied in descending priority order, so the one with the <em>lowest</em> priority runs last and
 * its value is the one that survives on a field more than one of them sets. That reading is easy to get backwards,
 * which is why it is pinned down here: a higher priority means applied earlier, not applied last.
 */
public class AgentCardCustomizerOrderingTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(TestAgent.class, EarlyCustomizer.class, LateCustomizer.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Name From Configuration");

    static final List<String> APPLIED = new CopyOnWriteArrayList<>();

    @PublicAgentCard
    Instance<AgentCard> agentCardInstance;

    @RegisterAiService
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
    public interface TestAgent {

        String chat(@UserMessage String question);
    }

    @Singleton
    public static class EarlyCustomizer implements AgentCardBuilderCustomizer {

        @Override
        public void customize(AgentCard.Builder cardBuilder) {
            APPLIED.add("early");
            cardBuilder.name("Name From The Early Customizer");
        }

        @Override
        public int priority() {
            return 100;
        }
    }

    @Singleton
    public static class LateCustomizer implements AgentCardBuilderCustomizer {

        @Override
        public void customize(AgentCard.Builder cardBuilder) {
            APPLIED.add("late");
            cardBuilder.name("Name From The Late Customizer");
        }

        @Override
        public int priority() {
            return -100;
        }
    }

    @Test
    public void testCustomizersAreAppliedInDescendingPriorityOrder() {
        APPLIED.clear();

        agentCardInstance.get();

        assertThat(APPLIED).containsExactly("early", "late");
    }

    /**
     * The customizer that runs last wins the field, including against the one the extension contributes itself from
     * configuration — which is what makes the priority worth exposing at all.
     */
    @Test
    public void testTheLastCustomizerAppliedWinsTheField() {
        assertThat(agentCardInstance.get().name()).isEqualTo("Name From The Late Customizer");
    }
}
