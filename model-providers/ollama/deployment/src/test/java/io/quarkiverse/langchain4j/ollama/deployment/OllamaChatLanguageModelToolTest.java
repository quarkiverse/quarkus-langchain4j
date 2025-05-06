package io.quarkiverse.langchain4j.ollama.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.assertj.core.api.Assertions.assertThat;

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
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ollama.*;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

class OllamaChatLanguageModelToolTest extends WiremockAware {

    private static final String scenario = "tools";
    private static final String secondState = "second";
    private static boolean called;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.ollama.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideConfigKey("quarkus.langchain4j.devservices.enabled", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.log-responses", "true");

    @Inject
    ChatModel chatLanguageModel;

    @Inject
    Bot bot;

    @BeforeEach
    void setup() {
        wiremock().resetMappings();
        wiremock().resetRequests();
        called = false;
    }

    @Test
    @ActivateRequestContext
    void doesFilterHallucinatedExecutionRequestAndCallPresentTool() {
        var firstResponse = """
                {
                  "model": "llama3.2",
                  "created_at": "2024-05-03T10:27:56.84235715Z",
                  "message": {
                    "role": "assistant",
                    "content": "I do not know the current time, I am sorry",
                    "tool_calls": [
                      {
                        "function" : {
                          "name": "getTime",
                          "arguments": { }
                        }
                      }, {
                        "function" : {
                          "name": "getName",
                          "arguments": { }
                        }
                      }
                    ]
                  },
                  "done": true,
                  "total_duration": 1206200561,
                  "load_duration": 695039,
                  "prompt_eval_duration": 18430000,
                  "eval_count": 105,
                  "eval_duration": 1057198000
                }
                """;

        var secondResponse = """
                {
                  "model": "llama3.2",
                  "created_at": "2024-05-03T10:27:56.84235715Z",
                  "message": {
                    "role": "assistant",
                    "content": "It is 13:00"
                  },
                  "done": true,
                  "total_duration": 1206200561,
                  "load_duration": 695039,
                  "prompt_eval_duration": 18430000,
                  "eval_count": 105,
                  "eval_duration": 1057198000
                }
                """;

        wiremock().register(
                post(urlEqualTo("/api/chat"))
                        .inScenario(scenario)
                        .whenScenarioStateIs(Scenario.STARTED)
                        .withRequestBody(matchingJsonPath("$.model", equalTo("llama3.2")))
                        .withHeader("Authorization", absent())
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(firstResponse)));

        wiremock().register(
                post(urlEqualTo("/api/chat"))
                        .inScenario(scenario)
                        .whenScenarioStateIs(secondState)
                        .withRequestBody(matchingJsonPath("$.model", equalTo("llama3.2")))
                        .withHeader("Authorization", absent())
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(secondResponse)));

        wiremock().setSingleScenarioState(scenario, Scenario.STARTED);

        var response = bot.chat("What time is it?");

        assertThat(response).isEqualTo("It is 13:00");
        assertThat(called).isTrue();
    }

    @RegisterAiService(tools = MyTool.class, toolHallucinationStrategy = MyHallucinationStrategy.class)
    interface Bot {
        String chat(String message);
    }

    @ApplicationScoped
    public static class MyTool {

        @ConfigProperty(name = "quarkus.wiremock.devservices.port")
        Integer wiremockPort;

        @Tool("getTime")
        public String getTime() {
            called = true;
            WireMock wireMock = new WireMock(wiremockPort);
            wireMock.setSingleScenarioState(scenario, secondState);
            return "13:00";
        }
    }

    @ApplicationScoped
    public static class MyHallucinationStrategy implements Function<ToolExecutionRequest, ToolExecutionResultMessage> {

        @Override
        public ToolExecutionResultMessage apply(ToolExecutionRequest request) {
            return ToolExecutionResultMessage.from(request, "no tool named '" + request.name() + "' found");
        }
    }
}
