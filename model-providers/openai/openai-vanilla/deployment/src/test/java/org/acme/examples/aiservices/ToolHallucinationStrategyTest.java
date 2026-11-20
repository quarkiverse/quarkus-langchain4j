package org.acme.examples.aiservices;

import static org.acme.examples.aiservices.OpenAiRequestAssertions.assertMultipleRequestMessage;
import static org.acme.examples.aiservices.OpenAiRequestAssertions.assertSingleRequestMessage;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.acme.examples.aiservices.OpenAiRequestAssertions.MessageContent;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkus.test.QuarkusExtensionTest;

public class ToolHallucinationStrategyTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClass(OpenAiRequestAssertions.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // about the Chat Completions API: without a base URL of its own the model would target the Responses API
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "chat-completion")
            .overrideConfigKey("quarkus.rest-client.rest-calculator.url", "http://localhost:${quarkus.http.test-port:8081}");

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setUp() {
        mock.reset();
    }

    @Inject
    Bot bot;

    @Test
    @ActivateRequestContext
    void should_execute_tool_then_answer() {
        var firstResponse = """
                {
                  "id": "chatcmpl-8D88Dag1gAKnOPP9Ed4bos7vSpaNz",
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
                          "name": "extractTown",
                          "arguments": "{\\n  \\"question\\": \\"What is the weather in Rome?\\"\\n}"
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
                          "id": "chatcmpl-8D88FIAUWSpwLaShFr0w8G1SWuVdl",
                          "object": "chat.completion",
                          "created": 1698140215,
                          "model": "gpt-3.5-turbo-0613",
                          "choices": [
                            {
                              "index": 0,
                              "message": {
                                "role": "assistant",
                                "content": "Rome"
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

        String userMessage = "What is the weather in Rome?";

        String answer = bot.chat(userMessage);

        assertThat(answer).isEqualTo("Rome");

        List<CapturedRequest> requests = mock.requests();
        assertThat(requests).hasSize(2);
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.path()).isEqualTo("/v1/chat/completions");
            assertThat(request.query()).isNull();
            assertThat(request.header("Authorization")).isEqualTo("Bearer whatever");
        });

        assertSingleRequestMessage(requests.get(0).bodyAsJson(), "What is the weather in Rome?");
        // the hallucination strategy's answer for the unknown tool is sent back to the model
        assertMultipleRequestMessage(requests.get(1).bodyAsJson(),
                List.of(
                        new MessageContent("user", "What is the weather in Rome?"),
                        new MessageContent("assistant", null),
                        new MessageContent("function", "Error: there is no tool called extractTown")));
    }

    @RegisterAiService(tools = CityExtractorAgent.class, toolHallucinationStrategy = MyHallucinationStrategy.class)
    interface Bot {
        String chat(String message);
    }

    @RegisterAiService
    public interface CityExtractorAgent {
        @UserMessage("""
                You are given one question and you have to extract city name from it
                Only reply the city name if it exists or reply 'unknown_city' if there is no city name in question

                Here is the question: {question}
                """)
        @Tool("Extracts the city from a question")
        String extractCity(String question);
    }

    @ApplicationScoped
    public static class MyHallucinationStrategy implements Function<ToolExecutionRequest, ToolExecutionResultMessage> {

        @Override
        public ToolExecutionResultMessage apply(ToolExecutionRequest toolExecutionRequest) {
            return ToolExecutionResultMessage.from(toolExecutionRequest,
                    "Error: there is no tool called " + toolExecutionRequest.name());
        }
    }
}
