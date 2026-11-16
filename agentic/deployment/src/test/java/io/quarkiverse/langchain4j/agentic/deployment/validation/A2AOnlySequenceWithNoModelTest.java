package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.a2a.A2AAgentCardException;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.V;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies that https://github.com/quarkiverse/quarkus-langchain4j/issues/2630 is fixed:
 * a sequence of two A2A-only agents should not require a ChatModel bean to be configured.
 */
public class A2AOnlySequenceWithNoModelTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(StoryGenerator.class, StoryEditor.class, StoryPipeline.class));

    @Test
    void a2aOnlySequenceShouldNotRequireChatModel() {
        // the agents have been successfully configured, including the A2A ones: there isn't any A2A server,
        // so the exception is expected, but only when the first A2A agent is invoked and fetches its agent card
        assertThatThrownBy(() -> pipeline.run("dragons and wizards"))
                .isInstanceOf(AgentInvocationException.class)
                .hasStackTraceContaining(A2AAgentCardException.class.getName()
                        + ": Failed to fetch the agent card of A2A agent 'generate' from http://localhost:9090");
    }

    @Inject
    StoryPipeline pipeline;

    public interface StoryGenerator {

        @A2AClientAgent(a2aServerUrl = "http://localhost:9090", description = "Generate a story", outputKey = "story")
        String generate(@V("topic") String topic);
    }

    public interface StoryEditor {

        @A2AClientAgent(a2aServerUrl = "http://localhost:9091", description = "Edit a story for clarity", outputKey = "story")
        String edit(@V("story") String story);
    }

    public interface StoryPipeline {

        @SequenceAgent(outputKey = "story", subAgents = { StoryGenerator.class, StoryEditor.class })
        String run(@V("topic") String topic);
    }
}
