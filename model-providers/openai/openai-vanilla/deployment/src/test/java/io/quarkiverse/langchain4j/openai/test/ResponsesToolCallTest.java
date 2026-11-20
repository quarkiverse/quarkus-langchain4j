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
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class ResponsesToolCallTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "my-key")
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true");

    @Inject
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void toolCallIsParsed() {
        mock.chat().reply(ChatReply.toolCall("getWeather", Map.of("city", "Paris")));

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("What's the weather in Paris?"))
                .toolSpecifications(ToolSpecification.builder()
                        .name("getWeather")
                        .description("Get the weather for a city")
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty("city")
                                .build())
                        .build())
                .build();

        ChatResponse response = chatModel.chat(request);

        assertThat(response.aiMessage().hasToolExecutionRequests()).isTrue();
        assertThat(response.aiMessage().toolExecutionRequests())
                .singleElement()
                .satisfies(toolCall -> {
                    assertThat(toolCall.name()).isEqualTo("getWeather");
                    // the id the mock gives the first tool call of a reply
                    assertThat(toolCall.id()).isEqualTo("call_mock_0");
                    assertThat(toolCall.arguments()).contains("Paris");
                });

        CapturedRequest captured = mock.chat().singleRequest();
        assertThat(captured.path()).isEqualTo("/v1/responses");
        assertThat(captured.query()).isNull();
    }
}
