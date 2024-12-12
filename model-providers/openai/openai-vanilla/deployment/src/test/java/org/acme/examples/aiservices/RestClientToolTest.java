package org.acme.examples.aiservices;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.ext.Provider;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import dev.langchain4j.agent.tool.Tool;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class RestClientToolTest extends OpenAiBaseTest {

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

        String userMessage = "What is the square root of 485906798473894056 in scientific notation?";

        String answer = bot.chat(userMessage);

        assertThat(answer).isEqualTo(
                "The square root of 485,906,798,473,894,056 in scientific notation is approximately 6.97070153193991E8.");

        assertThat(wiremock().getServeEvents()).hasSize(2);

        Map<String, Object> firstApiRequest = getRequestAsMap(getRequestBody(wiremock().getServeEvents().get(1)));
        assertSingleRequestMessage(firstApiRequest,
                "What is the square root of 485906798473894056 in scientific notation?");
        assertSingleFunction(firstApiRequest, "squareRoot");
        Map<String, Object> secondApiRequest = getRequestAsMap(getRequestBody(wiremock().getServeEvents().get(0)));
        assertMultipleRequestMessage(secondApiRequest,
                List.of(
                        new MessageContent("user",
                                "What is the square root of 485906798473894056 in scientific notation?"),
                        new MessageContent("assistant", null),
                        new MessageContent("function", "6.97070153193991E8")));
    }

    @RegisterAiService(tools = RestCalculator.class)
    interface Bot {

        String chat(String message);
    }

    @Singleton
    public static class CalculatorAfter implements Runnable {

        private final Integer wiremockPort;

        public CalculatorAfter(@ConfigProperty(name = "quarkus.wiremock.devservices.port") Integer wiremockPort) {
            this.wiremockPort = wiremockPort;
        }

        @Override
        public void run() {
            WireMock wireMock = new WireMock(wiremockPort);
            wireMock.setSingleScenarioState(scenario, secondState);
        }
    }

    @Path("calculator")
    @RegisterRestClient(configKey = "rest-calculator")
    @RegisterProvider(RestCalculator.ResponseFilter.class)
    interface RestCalculator {

        @POST
        @Tool("calculates the square root of the provided number")
        @Consumes("text/plain")
        @Produces("text/plain")
        double squareRoot(double number);

        @Provider
        class ResponseFilter implements ClientResponseFilter {

            @Inject
            CalculatorAfter after;

            @Override
            public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) {
                after.run();
            }
        }
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
