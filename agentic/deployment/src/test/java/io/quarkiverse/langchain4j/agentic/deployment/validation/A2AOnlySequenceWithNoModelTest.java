package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.enterprise.inject.CreationException;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.V;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that https://github.com/quarkiverse/quarkus-langchain4j/issues/2630 is fixed:
 * a sequence of two A2A-only agents should not require a ChatModel bean to be configured.
 */
public class A2AOnlySequenceWithNoModelTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(StoryGenerator.class, StoryEditor.class, StoryPipeline.class));

    @Test
    void a2aOnlySequenceShouldNotRequireChatModel() {
        // the exception is expected as there isn't any A2A server, but if we get there
        // it means that the agents have been successfully configured, including the A2A one
        assertThat(assertThrows(CreationException.class, () -> pipeline.run("dragons and wizards")))
                .hasMessageContaining("Failed to obtain agent card");
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
