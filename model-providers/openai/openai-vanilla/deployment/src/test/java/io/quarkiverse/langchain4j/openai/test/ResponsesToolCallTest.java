package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

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
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class ResponsesToolCallTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "my-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    ChatModel chatModel;

    @BeforeEach
    void reset() {
        resetRequests();
    }

    @Test
    void toolCallIsParsed() {
        wiremock().register(post(urlEqualTo("/v1/responses"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                        {
                                          "id": "resp_1",
                                          "model": "gpt-4o-mini",
                                          "status": "completed",
                                          "output": [
                                            {
                                              "type": "function_call",
                                              "call_id": "call_1",
                                              "name": "getWeather",
                                              "arguments": "{\\"city\\":\\"Paris\\"}"
                                            }
                                          ],
                                          "usage": { "input_tokens": 1, "output_tokens": 2, "total_tokens": 3 }
                                        }
                                        """)));

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
                    assertThat(toolCall.id()).isEqualTo("call_1");
                    assertThat(toolCall.arguments()).contains("Paris");
                });
    }
}
