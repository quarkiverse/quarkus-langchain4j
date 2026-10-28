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
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class ResponsesChatModelSmokeTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "my-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.responses.store", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.responses.max-output-tokens", "100");

    @Inject
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void blockingChatTargetsResponsesEndpoint() {
        mock.chat().reply(ChatReply.text("Hello from Responses API!").usage(1, 2));

        String response = chatModel.chat("hello");
        assertThat(response).isEqualTo("Hello from Responses API!");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/responses");
        assertThat(request.query()).isNull();
        assertThat(request.header("Authorization")).isEqualTo("Bearer my-key");

        JsonNode requestBody = request.bodyAsJson();
        assertThat(requestBody.path("model").textValue()).isEqualTo("gpt-6-luna");
        assertThat(requestBody.path("store").isBoolean()).isTrue();
        assertThat(requestBody.path("store").booleanValue()).isFalse();
        assertThat(requestBody.path("max_output_tokens").isInt()).isTrue();
        assertThat(requestBody.path("max_output_tokens").intValue()).isEqualTo(100);
        assertThat(requestBody.has("input")).isTrue();
        assertThat(requestBody.has("temperature")).isFalse();
        assertThat(requestBody.has("top_p")).isFalse();
    }
}
