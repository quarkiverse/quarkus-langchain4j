package io.quarkiverse.langchain4j.a2a.server.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkiverse.langchain4j.a2a.server.runtime.executor.QuarkusBaseAgentExecutor;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Exposes a declarative workflow of the agentic module rather than a plain AI Service. The workflow is not
 * annotated with {@code @RegisterAiService}, so it exercises the second detection path.
 */
public class AgenticWorkflowAgentTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(StoryWriter.class, StoryPolisher.class, StoryCreator.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Story Agent")
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.description", "Writes stories");

    @PublicAgentCard
    Instance<AgentCard> agentCardInstance;

    @Inject
    Instance<AgentExecutor> agentExecutorInstance;

    @RegisterAiService
    public interface StoryWriter {

        @SystemMessage("You are a creative writer.")
        @Agent(description = "Write a story about a topic", outputKey = "story")
        String write(@V("topic") @UserMessage String topic);
    }

    @RegisterAiService
    public interface StoryPolisher {

        @SystemMessage("You polish prose.")
        @Agent(description = "Polish a story", outputKey = "story")
        String polish(@V("story") @UserMessage String story);
    }

    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "write_story", name = "Write a story", description = "Writes and polishes a story about a topic", tags = "writing"))
    public interface StoryCreator {

        @SequenceAgent(outputKey = "story", subAgents = { StoryWriter.class, StoryPolisher.class })
        String create(@V("topic") String topic);
    }

    @Test
    public void testExecutorIsGeneratedForTheWorkflow() {
        assertThat(agentExecutorInstance.isResolvable()).isTrue();
        assertThat(agentExecutorInstance.get()).isInstanceOf(QuarkusBaseAgentExecutor.class);
    }

    @Test
    public void testCardDescribesTheWorkflow() {
        AgentCard agentCard = agentCardInstance.get();
        assertThat(agentCard.name()).isEqualTo("Story Agent");
        assertThat(agentCard.defaultInputModes()).containsExactly("text/plain");
        assertThat(agentCard.defaultOutputModes()).containsExactly("text/plain");
        assertThat(agentCard.skills()).singleElement()
                .satisfies(skill -> assertThat(skill.id()).isEqualTo("write_story"));
    }
}
