package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class ResponsesExplicitSamplingParamsTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "my-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.temperature", "0.3")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.top-p", "0.8");

    @Inject
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void explicitlyConfiguredValuesAreSent() {
        mock.chat().reply("hi");

        assertThat(chatModel.chat("hello")).isEqualTo("hi");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/responses");
        assertThat(request.query()).isNull();
        JsonNode requestBody = request.bodyAsJson();
        assertThat(requestBody.path("temperature").doubleValue()).isEqualTo(0.3);
        assertThat(requestBody.path("top_p").doubleValue()).isEqualTo(0.8);
    }
}
