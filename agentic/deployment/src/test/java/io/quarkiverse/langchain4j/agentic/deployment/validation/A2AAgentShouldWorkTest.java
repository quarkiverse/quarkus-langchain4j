package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.enterprise.inject.CreationException;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class A2AAgentShouldWorkTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(DummyChatModel.class, StoryCreator.class, CreativeWriter.class, AudienceEditor.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    StoryCreator storyCreator;

    @Test
    void test() {
        // the exception is expected as there isn't any A2A server, but if we get there
        // it means that the agents have been successfully configured, including the A2A one
        assertThat(assertThrows(CreationException.class, () -> storyCreator.write("dragons and wizards", "young adults")))
                .hasMessageContaining("Failed to obtain agent card");
    }

    public static class DummyChatModel implements ChatModel {

        private final String response;

        public DummyChatModel(String response) {
            this.response = response;
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(new AiMessage(response)).build();
        }
    }

    public interface CreativeWriter {

        @A2AClientAgent(a2aServerUrl = "http://localhost:8080", description = "Generate a story based on the given topic", outputKey = "story")
        String generateStory(@V("topic") String topic);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new DummyChatModel("story");
        }
    }

    public interface AudienceEditor {

        @UserMessage("""
                You are a professional editor.
                Analyze and rewrite the following story to better align with the target audience of {{audience}}.
                Return only the story and nothing else.
                The story is "{{story}}".
                """)
        @Agent(description = "Edit a story to better fit a given audience", outputKey = "story")
        String editStory(@V("story") String story, @V("audience") String audience);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new DummyChatModel("edited story");
        }
    }

    public interface StoryCreator {

        @SequenceAgent(outputKey = "story", subAgents = { CreativeWriter.class, AudienceEditor.class })
        String write(@V("topic") String topic, @V("audience") String audience);
    }
}
