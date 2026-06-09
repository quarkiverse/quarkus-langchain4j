package io.quarkiverse.langchain4j.agentic.deployment;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
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
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.V;
import io.quarkus.test.QuarkusUnitTest;

public class AgentReservedNameValidationTest {

    @RegisterExtension
    static final QuarkusUnitTest reservedNameTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(DevUi.class, DummySubAgent.class, DummyModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url", "http://localhost")
            .assertException(t -> Assertions.assertThat(t)
                    .isInstanceOf(IllegalConfigurationException.class)
                    .hasMessageContaining("reserved"));

    @Test
    void shouldFailBuild() {
        fail("Should not reach here — build should have failed");
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
     * Interface named DevUi auto-derives to config key "dev-ui", which is reserved.
     */
    public interface DevUi {
        @LoopAgent(description = "should fail", outputKey = "out", subAgents = { DummySubAgent.class })
        String process(@V("out") String input);

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
