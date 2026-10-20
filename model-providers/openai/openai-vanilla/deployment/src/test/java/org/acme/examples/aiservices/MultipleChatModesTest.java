package org.acme.examples.aiservices;

import static org.acme.examples.aiservices.OpenAiRequestAssertions.assertSingleRequestMessage;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class MultipleChatModesTest {

    public static final String MESSAGE_CONTENT = "Tell me a joke about developers";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClass(OpenAiRequestAssertions.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // about the Chat Completions API: without a base URL of its own the model would target the Responses API
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "chat-completion")
            .overrideConfigKey("quarkus.langchain4j.openai.model1.chat-model.mode", "chat-completion")
            .overrideConfigKey("quarkus.langchain4j.openai.model2.chat-model.mode", "chat-completion")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "defaultKey")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.model1.api-key", "key1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.model2.api-key", "key2");

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setup() {
        mock.reset();
        mock.chat().reply(MESSAGE_CONTENT);
    }

    @Inject
    ChatModel chatWithDefaultModel;

    @Inject
    @ModelName("model1")
    ChatModel chatWithModel1;

    @Inject
    @ModelName("model2")
    ChatModel chatWithModel2;

    @Test
    @ActivateRequestContext
    public void testDefaultModel() {
        String result = chatWithDefaultModel.chat(MESSAGE_CONTENT);
        assertThat(result).isEqualTo(MESSAGE_CONTENT);

        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(), MESSAGE_CONTENT);
    }

    @Test
    @ActivateRequestContext
    public void testNamedModel1() {
        String result = chatWithModel1.chat(MESSAGE_CONTENT);
        assertThat(result).isEqualTo(MESSAGE_CONTENT);

        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(), MESSAGE_CONTENT);
    }

    @Test
    @ActivateRequestContext
    public void testNamedModel2() {
        String result = chatWithModel2.chat(MESSAGE_CONTENT);
        assertThat(result).isEqualTo(MESSAGE_CONTENT);

        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(), MESSAGE_CONTENT);
    }

}
