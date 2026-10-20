package org.acme.examples.aiservices;

import static org.acme.examples.aiservices.OpenAiRequestAssertions.assertMultipleRequestMessage;
import static org.acme.examples.aiservices.OpenAiRequestAssertions.assertSingleFunction;
import static org.acme.examples.aiservices.OpenAiRequestAssertions.assertSingleRequestMessage;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import org.acme.examples.aiservices.OpenAiRequestAssertions.MessageContent;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.agent.tool.Tool;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkus.test.QuarkusExtensionTest;

public class RestClientToolboxTest {

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
                          "name": "squareRoot",
                          "arguments": "{\\n  \\"number\\": 485906798473894056\\n}"
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
                                "content": "The square root of 485,906,798,473,894,056 in scientific notation is approximately 6.97070153193991E8."
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

        String userMessage = "What is the square root of 485906798473894056 in scientific notation?";

        String answer = bot.chat(userMessage);

        assertThat(answer).isEqualTo(
                "The square root of 485,906,798,473,894,056 in scientific notation is approximately 6.97070153193991E8.");

        List<CapturedRequest> requests = mock.requests();
        assertThat(requests).hasSize(2);
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.path()).isEqualTo("/v1/chat/completions");
            assertThat(request.query()).isNull();
            assertThat(request.header("Authorization")).isEqualTo("Bearer whatever");
        });

        JsonNode firstApiRequest = requests.get(0).bodyAsJson();
        assertSingleRequestMessage(firstApiRequest,
                "What is the square root of 485906798473894056 in scientific notation?");
        assertSingleFunction(firstApiRequest, "squareRoot");
        assertMultipleRequestMessage(requests.get(1).bodyAsJson(),
                List.of(
                        new MessageContent("user",
                                "What is the square root of 485906798473894056 in scientific notation?"),
                        new MessageContent("assistant", null),
                        new MessageContent("function", "6.97070153193991E8")));
    }

    @RegisterAiService
    interface Bot {

        @ToolBox(RestCalculator.class)
        String chat(String message);
    }

    @Path("calculator")
    @RegisterRestClient(configKey = "rest-calculator")
    interface RestCalculator {

        @POST
        @Tool("calculates the square root of the provided number")
        @Consumes("text/plain")
        @Produces("text/plain")
        double squareRoot(double number);
    }

    @Path("calculator")
    public static class CalculatorResource {

        @POST
        @Consumes("text/plain")
        @Produces("text/plain")
        public double squareRoot(double number) {
            return Math.sqrt(number);
        }
    }

}
