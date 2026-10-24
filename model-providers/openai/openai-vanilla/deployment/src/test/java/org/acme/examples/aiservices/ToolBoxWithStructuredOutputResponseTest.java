package org.acme.examples.aiservices;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkus.test.QuarkusExtensionTest;

public class ToolBoxWithStructuredOutputResponseTest {
    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.response-format", "json_schema")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.strict-json-schema", "true")
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // about the Chat Completions API: without a base URL of its own the model would target the Responses API
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "chat-completion");
    @Inject
    MockModelServer mock;

    @Inject
    DivisionAfter divisionAfter;

    @BeforeEach
    void setUp() {
        mock.reset();
        divisionAfter.reset();
    }

    record DivisionResponse(
            @Description("The Quotient of the division operation") int quotient,
            @Description("The Reminder of the division operation") int remainder) {
    }

    record DivisionResult(int quotient, int remainder) {
    }

    @RegisterAiService
    @ApplicationScoped
    interface MathAgent {

        @ToolBox(DivisionTool.class)
        @UserMessage("""
                Divide the given number by the divisor

                Number: {{number}}
                Divisor: {{divisor}}
                """)
        DivisionResponse divide(int number, int divisor);
    }

    @Inject
    MathAgent mathAgent;

    @Singleton
    public static class DivisionAfter implements Runnable {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public void run() {
            calls.incrementAndGet();
        }

        int calls() {
            return calls.get();
        }

        void reset() {
            calls.set(0);
        }
    }

    @Singleton
    static class DivisionTool {
        private final Runnable after;

        DivisionTool(DivisionAfter after) {
            this.after = after;
        }

        @Tool("Divide the number by divisor")
        DivisionResult divide(int number, int divisor) {
            int quotient = number / divisor;
            int remainder = number % divisor;
            after.run();
            return new DivisionResult(quotient, remainder);
        }
    }

    @Test
    public void testStructuredResponseWithTool() throws JsonProcessingException {
        var firstResponse = """
                {
                  "id": "chatcmpl-BL2ebUEkjiLbhDCacCFFsLF7HAFCf",
                  "object": "chat.completion",
                  "created": 1698140213,
                  "model": "gpt-3.5-turbo-0613",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": null,
                        "function_call": {
                          "name": "divide",
                          "arguments": "{\\n\\"number\\": 102, \\"divisor\\": 5\\n}"
                        }
                      },
                      "finish_reason": "function_call"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 65,
                    "completion_tokens": 20,
                    "total_tokens": 85
                  }
                }
                """;

        var secondResponse = """
                        {
                          "id": "chatcmpl-Tf4swLfrW9UrW9uMmsN47HEX",
                          "object": "chat.completion",
                          "created": 1698140215,
                          "model": "gpt-3.5-turbo-0613",
                          "choices": [
                            {
                              "index": 0,
                              "message": {
                                "role": "assistant",
                                "content": "{\\n\\"quotient\\": 10, \\"remainder\\": 2\\n}"
                              },
                              "finish_reason": "stop"
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 102,
                            "completion_tokens": 33,
                            "total_tokens": 135
                          }
                        }
                """;

        // the fixtures use the legacy function_call format, which the neutral model cannot express
        mock.raw().on("POST", "/v1/chat/completions").replies(
                RawResponse.json(200, firstResponse),
                RawResponse.json(200, secondResponse));

        DivisionResponse response = mathAgent.divide(52, 5);

        assertThat(response.quotient).isEqualTo(10);
        assertThat(response.remainder).isEqualTo(2);

        assertThat(divisionAfter.calls()).isEqualTo(1);

        List<CapturedRequest> requests = mock.requests();
        assertThat(requests).hasSize(2);
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.path()).isEqualTo("/v1/chat/completions");
            assertThat(request.query()).isNull();
            assertThat(request.header("Authorization")).isEqualTo("Bearer whatever");
        });

        JsonNode secondApiRequest = requests.get(1).bodyAsJson();
        JsonNode lastMessage = secondApiRequest.get("messages").get(secondApiRequest.get("messages").size() - 1);
        assertThat(lastMessage.path("role").textValue()).isEqualTo("function");
        assertThat(lastMessage.path("name").textValue()).isEqualTo("divide");
        assertThat(new ObjectMapper().readTree(lastMessage.path("content").textValue()))
                .isEqualTo(new ObjectMapper().readTree("{\"quotient\": 20, \"remainder\": 2}"));

        JsonNode responseFormat = secondApiRequest.get("response_format");
        assertThat(responseFormat.path("type").textValue()).isEqualTo("json_schema");
        JsonNode jsonSchema = responseFormat.get("json_schema");
        assertThat(jsonSchema.path("name").textValue()).isEqualTo("DivisionResponse");
        assertThat(jsonSchema.has("schema")).isTrue();
        JsonNode properties = jsonSchema.get("schema").get("properties");
        assertThat(properties.has("quotient")).isTrue();
        assertThat(properties.has("remainder")).isTrue();
    }
}
