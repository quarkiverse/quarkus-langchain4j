package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class MultipleChatModesWithoutDefaultTest {

    public static final String MESSAGE_CONTENT = "Tell me a joke about developers";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.model1.api-key", "key1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.model2.api-key", "key2");

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setup() {
        mock.reset();
        mock.chat().reply(MESSAGE_CONTENT);
    }

    @Inject
    @ModelName("model1")
    ChatModel chatWithModel1;

    @Inject
    @ModelName("model2")
    ChatModel chatWithModel2;

    @Test
    @ActivateRequestContext
    public void testNamedModel1() {
        String result = chatWithModel1.chat(MESSAGE_CONTENT);
        assertThat(result).isEqualTo(MESSAGE_CONTENT);

        assertSingleRequestMessage(mock.chat().singleRequest(), "key1");
    }

    @Test
    @ActivateRequestContext
    public void testNamedModel2() {
        String result = chatWithModel2.chat(MESSAGE_CONTENT);
        assertThat(result).isEqualTo(MESSAGE_CONTENT);

        assertSingleRequestMessage(mock.chat().singleRequest(), "key2");
    }

    private static void assertSingleRequestMessage(CapturedRequest request, String apiKey) {
        assertThat(request.header("api-key")).isEqualTo(apiKey);
        JsonNode messages = request.bodyAsJson().get("messages");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).get("role").textValue()).isEqualTo("user");
        assertThat(messages.get(0).get("content").textValue()).isEqualTo(MESSAGE_CONTENT);
    }
}
