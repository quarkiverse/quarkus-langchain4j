package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class ModelNameAgentTest extends OpenAiBaseTest {

    static final String NAMED_MODEL_RESPONSE = "response-from-named-model";
    static final String DEFAULT_MODEL_RESPONSE = "response-from-default-model";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(NamedModelAgent.class, DefaultModelAgent.class, ChatModelProducers.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "default-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.mymodel.chat-model.provider", "openai")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.mymodel.api-key", "named-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.mymodel.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    public interface NamedModelAgent {

        @UserMessage("Answer the following question: {{question}}")
        @Agent(value = "An agent using a named model", outputKey = "answer")
        @ModelName("mymodel")
        String answer(@V("question") String question);
    }

    public interface DefaultModelAgent {

        @UserMessage("Answer the following question: {{question}}")
        @Agent(value = "An agent using the default model", outputKey = "answer")
        String answer(@V("question") String question);
    }

    @ApplicationScoped
    public static class ChatModelProducers {

        @Produces
        @ApplicationScoped
        @ModelName("mymodel")
        ChatModel namedModel() {
            return new FixedResponseChatModel(NAMED_MODEL_RESPONSE);
        }

        @Produces
        @ApplicationScoped
        ChatModel defaultModel() {
            return new FixedResponseChatModel(DEFAULT_MODEL_RESPONSE);
        }
    }

    public static class FixedResponseChatModel implements ChatModel {

        private final String response;

        FixedResponseChatModel(String response) {
            this.response = response;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(new AiMessage(response)).build();
        }
    }

    @Inject
    NamedModelAgent namedModelAgent;

    @Inject
    DefaultModelAgent defaultModelAgent;

    @Test
    void agentWithModelNameShouldUseNamedModel() {
        String result = namedModelAgent.answer("test");
        assertThat(result).isEqualTo(NAMED_MODEL_RESPONSE);
    }

    @Test
    void agentWithDefaultModelShouldUseDefaultModel() {
        String result = defaultModelAgent.answer("test");
        assertThat(result).isEqualTo(DEFAULT_MODEL_RESPONSE);
    }
}
