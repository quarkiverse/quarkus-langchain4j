package org.acme.examples.aiservices;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

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

import dev.langchain4j.agent.tool.ToolMemoryId;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkus.test.QuarkusUnitTest;

public class ToolWithToolMemoryIdTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(Tool.class, App.class));

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

        public App(Tool tool, @ConfigProperty(name = "quarkus.wiremock.devservices.port") Integer wiremockPort) {
            this.tool = tool;
            this.ai = AiServices.builder(Service.class)
                    .chatModel(createChatModel(wiremockPort))
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

        private OpenAiChatModel createChatModel(Integer wiremockPort) {
            return OpenAiChatModel.builder()
                    .baseUrl(String.format("http://localhost:%d/v1", wiremockPort))
                    .apiKey("whatever").build();
        }

    }

    @Inject
    App app;

    @Test
    @DisplayName("Verify tools invocation when @ToolMemoryId is used")
    void test() {
        app.invoke();
        app.invoke2();
    }

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

        wiremock().register(
                post(urlEqualTo("/v1/chat/completions"))
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
