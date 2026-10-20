package org.acme.examples.aiservices;

import static org.acme.examples.aiservices.OpenAiRequestAssertions.assertSingleRequestMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class MultipleChatModelsDeclarativeServiceTest {

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

    @RegisterAiService
    interface ChatWithDefaultModel {

        String chat(String userMessage);
    }

    @RegisterAiService(modelName = "model1")
    interface ChatWithModel1 {

        String chat(String userMessage);
    }

    @RegisterAiService(modelName = "model2")
    interface ChatWithModel2 {

        String chat(String userMessage);
    }

    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface ChatWithRuntimeSelection {

        String chat(@UserMessage String userMessage, @ModelName String model);
    }

    @Inject
    ChatWithDefaultModel chatWithDefaultModel;

    @Inject
    ChatWithModel1 chatWithModel1;

    @Inject
    ChatWithModel2 chatWithModel2;

    @Inject
    ChatWithRuntimeSelection chatWithRuntimeSelection;

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

    @Test
    @ActivateRequestContext
    public void testRuntimeSelection() {
        String result = chatWithRuntimeSelection.chat(MESSAGE_CONTENT, null);
        assertThat(result).isEqualTo(MESSAGE_CONTENT);
        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(), MESSAGE_CONTENT);
        assertThat(mock.chat().singleRequest().header("Authorization")).isEqualTo("Bearer defaultKey");

        setup();

        result = chatWithRuntimeSelection.chat(MESSAGE_CONTENT, "model1");
        assertThat(result).isEqualTo(MESSAGE_CONTENT);
        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(), MESSAGE_CONTENT);
        assertThat(mock.chat().singleRequest().header("Authorization")).isEqualTo("Bearer key1");

        setup();

        result = chatWithRuntimeSelection.chat(MESSAGE_CONTENT, "model2");
        assertThat(result).isEqualTo(MESSAGE_CONTENT);
        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(), MESSAGE_CONTENT);
        assertThat(mock.chat().singleRequest().header("Authorization")).isEqualTo("Bearer key2");

        setup();

        assertThatThrownBy(() -> {
            chatWithRuntimeSelection.chat(MESSAGE_CONTENT, "dummy");
        });
        assertThat(mock.requests()).isEmpty();
    }

}
