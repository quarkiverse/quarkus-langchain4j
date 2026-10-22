package io.quarkiverse.langchain4j.a2a.server.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentSkill;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.AgentCardBuilderCustomizer;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * A customizer that declares no priority shares the default one, and the extension's own customizers must not
 * share it with it: among equal priorities the order is whatever order the container lists the beans in, so the
 * extension could run last and silently restore the name from configuration or the skills from the annotation.
 */
public class DefaultPriorityCustomizerTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(TestAgent.class, OverridingCustomizer.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Name From Configuration");

    @PublicAgentCard
    Instance<AgentCard> agentCardInstance;

    @RegisterAiService
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
    public interface TestAgent {

        String chat(@UserMessage String question);
    }

    @Singleton
    public static class OverridingCustomizer implements AgentCardBuilderCustomizer {

        @Override
        public void customize(AgentCard.Builder cardBuilder) {
            cardBuilder.name("Name From The Application")
                    .skills(List.of(AgentSkill.builder()
                            .id("forecast")
                            .name("Forecast")
                            .description("Forecasts the weather")
                            .tags(List.of("weather"))
                            .build()));
        }
    }

    @Test
    public void testTheApplicationOverridesTheConfiguration() {
        assertThat(agentCardInstance.get().name()).isEqualTo("Name From The Application");
    }

    @Test
    public void testTheApplicationOverridesTheAnnotation() {
        assertThat(agentCardInstance.get().skills()).extracting(AgentSkill::id).containsExactly("forecast");
    }
}
