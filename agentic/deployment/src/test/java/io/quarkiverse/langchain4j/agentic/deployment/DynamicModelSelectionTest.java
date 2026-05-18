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
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class DynamicModelSelectionTest extends OpenAiBaseTest {

    static final String FIRST_MODEL_RESPONSE = "first";
    static final String SECOND_MODEL_RESPONSE = "second";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(EchoAgent.class, DynamicSequenceAgent.class, DynamicModelAgent.class,
                                    EchoAgent.class, ChatModelProducers.class,
                                    Agents.FixedResponseChatModel.class, Agents.EchoResponseChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "default-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.echoModel.chat-model.provider", "openai")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.echoModel.api-key", "named-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.echoModel.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.firstModel.chat-model.provider", "openai")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.firstModel.api-key", "named-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.firstModel.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.secondModel.chat-model.provider", "openai")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.secondModel.api-key", "named-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.secondModel.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    public interface EchoAgent {

        @UserMessage("{{text}}")
        @Agent(description = "An agent echoing its input", outputKey = "echo")
        String echo(String text);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.EchoResponseChatModel();
        }
    }

    public interface DynamicModelAgent {

        @UserMessage("Answer the following question: {{text}}")
        @Agent(value = "An agent using a dynamic model selection", outputKey = "answer")
        String answer(String text);

        @ChatModelSupplier
        static ChatModel chatModel(String echo) {
            return echo.contains("a") ? new Agents.FixedResponseChatModel(FIRST_MODEL_RESPONSE)
                    : new Agents.FixedResponseChatModel(SECOND_MODEL_RESPONSE);
        }
    }

    public interface DynamicSequenceAgent {

        @SequenceAgent(outputKey = "answer", subAgents = { EchoAgent.class, DynamicModelAgent.class })
        String answer(String text);
    }

    @ApplicationScoped
    public static class ChatModelProducers {

        @Produces
        @ApplicationScoped
        @ModelName("firstModel")
        ChatModel firstModel() {
            return new Agents.FixedResponseChatModel(FIRST_MODEL_RESPONSE);
        }

        @Produces
        @ApplicationScoped
        @ModelName("secondModel")
        ChatModel secondModel() {
            return new Agents.FixedResponseChatModel(SECOND_MODEL_RESPONSE);
        }

        @Produces
        @ApplicationScoped
        @ModelName("echoModel")
        ChatModel echoModel() {
            return new Agents.EchoResponseChatModel();
        }
    }

    @Inject
    DynamicSequenceAgent dynamicSequenceAgent;

    @Test
    void agentWithModelNameShouldUseNamedModel() {
        String result = dynamicSequenceAgent.answer("cat");
        assertThat(result).isEqualTo(FIRST_MODEL_RESPONSE);
    }

    @Test
    void agentWithDefaultModelShouldUseDefaultModel() {
        String result = dynamicSequenceAgent.answer("dog");
        assertThat(result).isEqualTo(SECOND_MODEL_RESPONSE);
    }
}
