package org.acme.examples.aiservices;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class ToolHallucinationStrategyTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideConfigKey("quarkus.rest-client.rest-calculator.url", "http://localhost:${quarkus.http.test-port:8081}");

    private static final String scenario = "tools";
    private static final String secondState = "second";

    @BeforeEach
    void setUp() {
        wiremock().resetMappings();
        wiremock().resetRequests();
    }

    @Inject
    Bot bot;

    @Test
    @ActivateRequestContext
    void should_execute_tool_then_answer() throws IOException {
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

        wiremock().register(
                post(urlEqualTo("/v1/chat/completions"))
                        .withHeader("Authorization", equalTo("Bearer whatever"))
                        .inScenario(scenario)
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(firstResponse)));
        wiremock().register(
                post(urlEqualTo("/v1/chat/completions"))
                        .withHeader("Authorization", equalTo("Bearer whatever"))
                        .inScenario(scenario)
                        .whenScenarioStateIs(secondState)
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(secondResponse)));

        wiremock().setSingleScenarioState(scenario, Scenario.STARTED);

        String userMessage = "What is the weather in Rome?";

        String answer = bot.chat(userMessage);

        assertThat(answer).isEqualTo("Rome");

        assertThat(wiremock().getServeEvents()).hasSize(2);
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
        private final Integer wiremockPort;

        public MyHallucinationStrategy(@ConfigProperty(name = "quarkus.wiremock.devservices.port") Integer wiremockPort) {
            this.wiremockPort = wiremockPort;
        }

        @Override
        public ToolExecutionResultMessage apply(ToolExecutionRequest toolExecutionRequest) {
            WireMock wireMock = new WireMock(wiremockPort);
            wireMock.setSingleScenarioState(scenario, secondState);
            return ToolExecutionResultMessage.from(toolExecutionRequest,
                    "Error: there is no tool called " + toolExecutionRequest.name());
        }
    }
}
