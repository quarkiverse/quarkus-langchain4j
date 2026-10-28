package org.acme.examples.aiservices;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;

/**
 * Base class to verify the tools invocation in various scopes.
 */
public abstract class ToolsScopeTestBase {

    /**
     * The most {@link MyApp#invoke(int)} calls a test makes.
     */
    private static final int MAX_INVOCATIONS = 4;

    private static final String FUNCTION_CALL_RESPONSE = """
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
                         "arguments": "{\\n  \\"content\\": \\"Hello\\"\\n}"
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

    private static final String ANSWER_RESPONSE = """
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

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setup() {
        mock.reset();
        // each invocation is a tool call followed by the answer; the fixtures use the legacy function_call format,
        // which the neutral model cannot express
        RawResponse[] responses = new RawResponse[2 * MAX_INVOCATIONS];
        for (int i = 0; i < MAX_INVOCATIONS; i++) {
            responses[2 * i] = RawResponse.json(200, FUNCTION_CALL_RESPONSE);
            responses[2 * i + 1] = RawResponse.json(200, ANSWER_RESPONSE);
        }
        mock.raw().on("POST", "/v1/chat/completions").replies(responses);
    }

    public interface MyService {
        String sayHello(String input);
    }

    public interface MyTool {
        /**
         * Invoked by the LLM.
         *
         * @param content the content
         * @return ignored
         */
        String doSomething(String content);

        int called();
    }

    @ApplicationScoped
    public static class MyApp {

        private final MyService ai;
        private final MyTool tool;
        private final MockModelServer mock;

        public MyApp(MyTool tool, MockModelServer mock,
                @ConfigProperty(name = "quarkus.langchain4j.openai.base-url") String baseUrl) {
            this.tool = tool;
            this.mock = mock;

            ChatMemoryProvider chatMemoryProvider = memoryId -> MessageWindowChatMemory.builder()
                    .id(memoryId)
                    .maxMessages(10)
                    .build();

            this.ai = AiServices.builder(MyService.class)
                    .chatModel(createChatModel(baseUrl))
                    .tools(tool)
                    .chatMemoryProvider(chatMemoryProvider)
                    .build();
        }

        /**
         * Invokes the LLM and checks the tool has been called {@code expectedCalled}.
         *
         * @param expectedCalled the expected number of times the tool has been called
         */
        public void invoke(int expectedCalled) {
            String actual = ai.sayHello("ignored...");
            assertThat(actual).isEqualTo("Do something has been called.");
            assertThat(tool.called()).isEqualTo(expectedCalled);

            List<CapturedRequest> requests = mock.requests();
            assertThat(requests).hasSizeGreaterThanOrEqualTo(2);
            assertThat(requests).allSatisfy(request -> {
                assertThat(request.path()).isEqualTo("/v1/chat/completions");
                assertThat(request.query()).isNull();
                assertThat(request.header("Authorization")).isEqualTo("Bearer whatever");
            });
            // the answer was requested with the tool result
            JsonNode messages = requests.get(requests.size() - 1).bodyAsJson().get("messages");
            JsonNode last = messages.get(messages.size() - 1);
            assertThat(last.path("role").textValue()).isEqualTo("function");
            assertThat(last.path("name").textValue()).isEqualTo("doSomething");
            assertThat(last.path("content").textValue()).isEqualTo("ignored");
        }

        private OpenAiChatModel createChatModel(String baseUrl) {
            return OpenAiChatModel.builder().baseUrl(baseUrl)
                    .apiKey("whatever").build();
        }

    }

}
