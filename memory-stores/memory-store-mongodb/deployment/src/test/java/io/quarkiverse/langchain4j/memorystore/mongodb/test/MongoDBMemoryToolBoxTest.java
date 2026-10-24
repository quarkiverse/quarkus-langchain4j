package io.quarkiverse.langchain4j.memorystore.mongodb.test;

import static io.quarkiverse.langchain4j.memorystore.mongodb.test.OpenAiRequestAssertions.assertMultipleRequestMessage;
import static io.quarkiverse.langchain4j.memorystore.mongodb.test.OpenAiRequestAssertions.assertSingleFunction;
import static io.quarkiverse.langchain4j.memorystore.mongodb.test.OpenAiRequestAssertions.assertSingleRequestMessage;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.agent.tool.Tool;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.memorystore.mongodb.test.OpenAiRequestAssertions.MessageContent;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkus.test.QuarkusExtensionTest;

public class MongoDBMemoryToolBoxTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClass(OpenAiRequestAssertions.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // about the Chat Completions API: without a base URL of its own the model would target the Responses API
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "chat-completion");
    @Inject
    MockModelServer mock;

    @Inject
    CalculatorAfter calculatorAfter;

    @BeforeEach
    void setUp() {
        mock.reset();
        calculatorAfter.reset();
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

        String answer = bot.chat2(userMessage);

        assertThat(answer).isEqualTo(
                "The square root of 485,906,798,473,894,056 in scientific notation is approximately 6.97070153193991E8.");

        assertThat(calculatorAfter.calls()).isEqualTo(1);

        List<CapturedRequest> requests = mock.requests();
        assertThat(requests).hasSize(2);
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.path()).isEqualTo("/v1/chat/completions");
            assertThat(request.query()).isNull();
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

    @Test
    @ActivateRequestContext
    void should_not_use_tool() {
        mock.chat().reply("I cannot calculate that.");

        String result = bot.chat("What is the square root of 485906798473894056 in scientific notation?");
        assertThat(result).isEqualTo("I cannot calculate that.");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.query()).isNull();
        assertSingleRequestMessage(request.bodyAsJson(),
                "What is the square root of 485906798473894056 in scientific notation?");
        assertSingleFunction(request.bodyAsJson(), "fooBar");
    }

    @RegisterAiService(tools = Dummy.class)
    interface Bot {

        String chat(String message);

        @ToolBox(Calculator.class)
        String chat2(String message);
    }

    @Singleton
    public static class CalculatorAfter implements Runnable {

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
    static class Calculator {

        private final Runnable after;

        Calculator(CalculatorAfter after) {
            this.after = after;
        }

        @Tool("calculates the square root of the provided number")
        double squareRoot(double number) {
            var result = Math.sqrt(number);
            after.run();
            return result;
        }
    }

    @Singleton
    static class Dummy {

        @Tool("Does not do anything useful")
        String fooBar(String foo) {
            return "bar";
        }
    }
}
