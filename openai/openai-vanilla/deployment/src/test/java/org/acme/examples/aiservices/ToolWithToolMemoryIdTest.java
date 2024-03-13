package org.acme.examples.aiservices;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.WireMockServer;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import io.quarkiverse.langchain4j.openai.test.WiremockUtils;
import io.quarkus.test.QuarkusUnitTest;

public class ToolWithToolMemoryIdTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(WiremockUtils.class,
                    Tool.class, App.class));

    static WireMockServer wireMockServer;

    @Singleton
    public static class Tool {

        final AtomicReference<String> contentValue = new AtomicReference<>();
        final AtomicReference<Object> memoryIdValue = new AtomicReference<>();
        final AtomicReference<String> content2Value = new AtomicReference<>();

        @dev.langchain4j.agent.tool.Tool
        public String doSomething(String content, @ToolMemoryId Object memoryId, String content2) {
            contentValue.set(content);
            content2Value.set(content2);
            memoryIdValue.set(memoryId);
            return "ignored";
        }
    }

    public interface Service {

        String sayHello(String input);
    }

    @ApplicationScoped
    public static class App {

        private final Service ai;
        private final Tool tool;

        public App(Tool tool) {
            this.tool = tool;
            this.ai = AiServices.builder(Service.class)
                    .chatLanguageModel(createChatModel())
                    .tools(tool)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .build();
        }

        public void invoke() {
            String actual = ai.sayHello("ignored...");
            assertThat(actual).isEqualTo("Do something has been called.");
            assertThat(tool.contentValue.get()).isEqualTo("Hello");
            assertThat(tool.content2Value.get()).isEqualTo("Hello2");
            assertThat(tool.memoryIdValue.get()).isEqualTo("default");
        }

    }

    @Inject
    App app;

    @Test
    @DisplayName("Verify tools invocation when @ToolMemoryId is used")
    void test() {
        app.invoke();
    }

    private static OpenAiChatModel createChatModel() {
        return OpenAiChatModel.builder().baseUrl("http://localhost:8089/v1")
                .apiKey("whatever").build();
    }

    @BeforeAll
    static void beforeAll() {
        wireMockServer = new WireMockServer(options().port(8089));
        wireMockServer.start();
    }

    @AfterAll
    static void afterAll() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setup() {
        wireMockServer.resetAll();
        wireMockServer.stubFor(
                post(urlEqualTo("/v1/chat/completions"))
                        .withHeader("Authorization", equalTo("Bearer whatever"))
                        .inScenario("Tool")
                        .whenScenarioStateIs(STARTED)
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
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
                                                          \s"""))
                        .willSetStateTo("Step two"));

        wireMockServer.stubFor(
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
}
