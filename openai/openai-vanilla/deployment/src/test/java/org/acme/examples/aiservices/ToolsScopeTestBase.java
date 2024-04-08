package org.acme.examples.aiservices;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;

/**
 * Base class to verify the tools invocation in various scopes.
 */
public abstract class ToolsScopeTestBase extends OpenAiBaseTest {

    @BeforeEach
    void setup() {
        resetRequests();
        wiremock().register(
                post(urlEqualTo("/v1/chat/completions"))
                        .inScenario("Tool")
                        .whenScenarioStateIs(STARTED)
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("""
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
                                                  \s"""))
                        .willSetStateTo("Step two"));

        wiremock().register(
                post(urlEqualTo("/v1/chat/completions"))
                        .withHeader("Authorization", equalTo("Bearer whatever"))
                        .inScenario("Tool")
                        .whenScenarioStateIs("Step two")
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("""
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
                                                  \s"""))
                        .willSetStateTo(STARTED));
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

        public MyApp(MyTool tool, @ConfigProperty(name = "quarkus.wiremock.devservices.port") Integer wiremockPort) {
            this.tool = tool;
            this.ai = AiServices.builder(MyService.class)
                    .chatLanguageModel(createChatModel(wiremockPort))
                    .tools(tool)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
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
        }

        private OpenAiChatModel createChatModel(Integer wiremockPort) {
            return OpenAiChatModel.builder().baseUrl(String.format("http://localhost:%d/v1", wiremockPort))
                    .apiKey("whatever").build();
        }

    }

}
