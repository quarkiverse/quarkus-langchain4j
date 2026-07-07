package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class DuplicateAgentNamesTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .assertException(
                    throwable -> Assertions.assertThat(throwable).isInstanceOf(IllegalConfigurationException.class)
                            .hasMessageContaining("uplicate agent name")
                            .hasMessageContaining("writer"));

    @Test
    public void test() {
        fail("should never be called");
    }

    @Singleton
    public static class StoryCreatorConsumer {

        @Inject
        StoryCreator storyCreator;
    }

    public interface StoryCreator {

        @SequenceAgent(outputKey = "story", subAgents = { CreativeWriter.class, AudienceEditor.class })
        String write(@V("topic") String topic, @V("audience") String audience);
    }

    public interface CreativeWriter {

        @UserMessage("""
                You are a creative writer.
                Generate a draft of a story long no more than 3 sentence around the given topic.
                Return only the story and nothing else.
                The topic is {{topic}}.
                """)
        @Agent(name = "writer", description = "Generate a story based on the given topic", outputKey = "story-initial")
        String generateStory(@V("topic") String topic);
    }

    public interface AudienceEditor {

        @UserMessage("""
                You are a professional editor.
                Analyze and rewrite the following story to better align with the target audience of {{audience}}.
                Return only the story and nothing else.
                The story is "{{story-initial}}".
                """)
        @Agent(name = "writer", description = "Edit a story to better fit a given audience", outputKey = "story")
        String editStory(@V("story-initial") String story, @V("audience") String audience);
    }
}
