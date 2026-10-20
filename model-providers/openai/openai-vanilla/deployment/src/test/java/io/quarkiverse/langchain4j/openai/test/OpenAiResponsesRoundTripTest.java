package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class OpenAiResponsesRoundTripTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "openai")
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "responses");

    @Inject
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void textReply() {
        mock.chat().reply("Hello from Responses");

        assertThat(chatModel.chat("hi")).isEqualTo("Hello from Responses");
        assertThat(mock.chat().singleRequest().path()).endsWith("/v1/responses");
    }

    @Test
    void toolCallReply() {
        mock.chat().reply(ChatReply.toolCall("getWeather", Map.of("city", "Paris")));

        ChatResponse response = chatModel.chat(ChatRequest.builder().messages(UserMessage.from("weather?"))
                .toolSpecifications(ToolSpecification.builder().name("getWeather")
                        .parameters(JsonObjectSchema.builder().addStringProperty("city").build()).build())
                .build());

        assertThat(response.aiMessage().hasToolExecutionRequests()).isTrue();
        assertThat(response.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("getWeather");
        assertThat(response.aiMessage().toolExecutionRequests().get(0).arguments()).contains("Paris");
        assertThat(mock.chat().singleRequest().path()).endsWith("/v1/responses");
    }
}
