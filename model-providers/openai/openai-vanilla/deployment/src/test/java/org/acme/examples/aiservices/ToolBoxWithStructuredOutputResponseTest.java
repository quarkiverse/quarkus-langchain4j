package org.acme.examples.aiservices;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.map;

import java.io.IOException;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class ToolBoxWithStructuredOutputResponseTest extends OpenAiBaseTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.response-format", "json_schema")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.strict-json-schema", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    private static final String scenario = "tools";
    private static final String secondState = "second";

    @BeforeEach
    void setUp() {
        wiremock().resetMappings();
        wiremock().resetRequests();
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

        private final Integer wiremockPort;

        public DivisionAfter(@ConfigProperty(name = "quarkus.wiremock.devservices.port") Integer wiremockPort) {
            this.wiremockPort = wiremockPort;
        }

        @Override
        public void run() {
            WireMock wireMock = new WireMock(wiremockPort);
            wireMock.setSingleScenarioState(scenario, secondState);
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
    public void testStructuredResponseWithTool() throws IOException {
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

        DivisionResponse response = mathAgent.divide(52, 5);

        assertThat(response.quotient).isEqualTo(10);
        assertThat(response.remainder).isEqualTo(2);

        Map<String, Object> requestAsMap = getRequestAsMap(getRequestBody(wiremock().getServeEvents().get(0)));
        assertThat(requestAsMap).hasEntrySatisfying("response_format", (v) -> {
            assertThat(v).asInstanceOf(map(String.class, Object.class)).satisfies(responseFormatMap -> {
                assertThat(responseFormatMap).containsEntry("type", "json_schema");
                assertThat(responseFormatMap).extracting("json_schema").satisfies(js -> {
                    assertThat(js).asInstanceOf(map(String.class, Object.class)).satisfies(jsonSchemaMap -> {
                        assertThat(jsonSchemaMap).containsEntry("name", "DivisionResponse").containsKey("schema");
                        assertThat(jsonSchemaMap).extracting("schema").satisfies(sjs -> {
                            assertThat(sjs).asInstanceOf(map(String.class, Object.class)).satisfies(schemaMap -> {
                                assertThat(schemaMap).containsKey("properties");
                                assertThat(schemaMap).extracting("properties").satisfies(pjs -> {
                                    assertThat(pjs).asInstanceOf(map(String.class, Object.class)).satisfies(propertiesMap -> {
                                        assertThat(propertiesMap).containsKey("quotient").containsKey("remainder");
                                    });
                                });
                            });
                        });
                    });
                });
            });
        });
    }
}
