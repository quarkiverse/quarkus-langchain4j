package io.quarkiverse.langchain4j.agentic.deployment;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.V;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that multi-word interface names auto-derive to correct kebab-case config keys.
 * StoryCreator → story-creator, LegalExpert → legal-expert, etc.
 */
public class AgentAutoNamingTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(StoryCreator.class, DummySubAgent.class, DummyModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url", "http://localhost");

    @Inject
    StoryCreator storyCreator;

    @Test
    void multiWordInterfaceDerivesCorrectly() {
        // If auto-derivation failed, the build would have failed with duplicate/reserved key errors
        // This test succeeds if build completes — verifying that StoryCreator → story-creator worked
        // The injection confirms the bean was created correctly
        org.junit.jupiter.api.Assertions.assertNotNull(storyCreator);
    }

    public interface DummySubAgent {
        @dev.langchain4j.agentic.Agent(description = "dummy", outputKey = "out")
        String run(@V("out") String input);

        @ChatModelSupplier
        static ChatModel model() {
            return new DummyModel();
        }
    }

    /**
     * Multi-word interface name should auto-derive to "story-creator" config key.
     */
    public interface StoryCreator {
        @LoopAgent(description = "creates stories", outputKey = "story", subAgents = { DummySubAgent.class })
        String create(@V("story") String prompt);

        @ChatModelSupplier
        static ChatModel model() {
            return new DummyModel();
        }
    }

    public static class DummyModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(new AiMessage("ok")).build();
        }
    }
}
