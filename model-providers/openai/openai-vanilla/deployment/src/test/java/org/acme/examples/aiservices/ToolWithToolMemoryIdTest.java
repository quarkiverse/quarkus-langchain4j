package org.acme.examples.aiservices;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.agent.tool.ToolMemoryId;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkus.test.QuarkusExtensionTest;

public class ToolWithToolMemoryIdTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(Tool.class, App.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "defaultKey")
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // about the Chat Completions API: without a base URL of its own the model would target the Responses API
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "chat-completion");

    @Singleton
    public static class Tool {

        final AtomicReference<String> contentValue = new AtomicReference<>();
        final AtomicReference<Object> memoryIdValue = new AtomicReference<>();
        final AtomicReference<String> content2Value = new AtomicReference<>();
        final AtomicReference<InvocationParameters> paramsValue = new AtomicReference<>();

        @dev.langchain4j.agent.tool.Tool
        public String doSomething(String content, @ToolMemoryId Object memoryId, InvocationParameters params, String content2) {
            contentValue.set(content);
            content2Value.set(content2);
            memoryIdValue.set(memoryId);
            paramsValue.set(params);
            return "ignored";
        }
    }

    public interface Service {

        String sayHello(String input);

        String sayHello(@UserMessage String input, InvocationParameters params);
    }

    @ApplicationScoped
    public static class App {

        private final Service ai;
        private final Tool tool;

        public App(Tool tool, @ConfigProperty(name = "quarkus.langchain4j.openai.base-url") String baseUrl) {
            this.tool = tool;
            this.ai = AiServices.builder(Service.class)
                    .chatModel(createChatModel(baseUrl))
                    .tools(tool)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .build();
        }

        public void invoke() {
            InvocationParameters params = new InvocationParameters();
            params.put("key", "value");
            String actual = ai.sayHello("ignored...", params);
            assertThat(actual).isEqualTo("Do something has been called.");
            assertThat(tool.contentValue.get()).isEqualTo("Hello");
            assertThat(tool.content2Value.get()).isEqualTo("Hello2");
            assertThat(tool.memoryIdValue.get()).isEqualTo("default");
            assertThat(tool.paramsValue.get()).isEqualTo(params);
        }

        public void invoke2() {
            String actual = ai.sayHello("ignored...");
            assertThat(actual).isEqualTo("Do something has been called.");
            assertThat(tool.contentValue.get()).isEqualTo("Hello");
            assertThat(tool.content2Value.get()).isEqualTo("Hello2");
            assertThat(tool.memoryIdValue.get()).isEqualTo("default");
        }

        private OpenAiChatModel createChatModel(String baseUrl) {
            return OpenAiChatModel.builder()
                    .baseUrl(baseUrl)
                    .apiKey("whatever").build();
        }

    }

    @Inject
    App app;

    @Inject
    MockModelServer mock;

    @Test
    @DisplayName("Verify tools invocation when @ToolMemoryId is used")
    void test() {
        app.invoke();
        app.invoke2();

        List<CapturedRequest> requests = mock.requests();
        assertThat(requests).hasSize(4);
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.path()).isEqualTo("/v1/chat/completions");
            assertThat(request.query()).isNull();
            assertThat(request.header("Authorization")).isEqualTo("Bearer whatever");
        });
        // each answer was requested with the tool result
        for (int i : new int[] { 1, 3 }) {
            JsonNode messages = requests.get(i).bodyAsJson().get("messages");
            JsonNode last = messages.get(messages.size() - 1);
            assertThat(last.path("role").textValue()).isEqualTo("function");
            assertThat(last.path("name").textValue()).isEqualTo("doSomething");
            assertThat(last.path("content").textValue()).isEqualTo("ignored");
        }
    }

    @BeforeEach
    void setup() {
        mock.reset();
        // each invocation is a tool call followed by the answer; the fixtures use the legacy function_call format,
        // which the neutral model cannot express
        String functionCallResponse = """
                {
                     "id": "chatcmpl-8GRu1BOlVqip3s1EKxD1YDJENPZNm",
                     "object": "chat.completion",
                     "created": 1698931197,
                     "model": "gpt-3.5-turbo-0613",
                     "choices": [
                       {
                         "index": 0,
                         "message": {
                           "role": "assistant",
                           "content": null,
                           "function_call": {
                             "name": "doSomething",
                             "arguments": "{\\n  \\"content\\": \\"Hello\\", \\"content2\\": \\"Hello2\\"\\n}"
                           }
                         },
                         "finish_reason": "function_call"
                       }
                     ],
                     "usage": {
                       "prompt_tokens": 97,
                       "completion_tokens": 52,
                       "total_tokens": 149
                     }
                   }
                  \s""";
        String answerResponse = """
                {
                        "id": "chatcmpl-8GRu6o9Qf9JFAebDqpj76H5fl6Naz",
                        "object": "chat.completion",
                        "created": 1698931202,
                        "model": "gpt-3.5-turbo-0613",
                        "choices": [
                          {
                            "index": 0,
                            "message": {
                              "role": "assistant",
                              "content": "Do something has been called."
                            },
                            "finish_reason": "stop"
                          }
                        ],
                        "usage": {
                          "prompt_tokens": 159,
                          "completion_tokens": 13,
                          "total_tokens": 172
                        }
                      }
                  \s""";
        mock.raw().on("POST", "/v1/chat/completions").replies(
                RawResponse.json(200, functionCallResponse),
                RawResponse.json(200, answerResponse),
                RawResponse.json(200, functionCallResponse),
                RawResponse.json(200, answerResponse));
    }
}
