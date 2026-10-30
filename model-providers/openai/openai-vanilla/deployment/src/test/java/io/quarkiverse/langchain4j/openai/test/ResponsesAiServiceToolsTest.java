package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.stubbing.Scenario;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Drives a full AI service tool loop over the Responses API: the model asks for a tool, the tool runs, the result is
 * sent back as a {@code function_call_output} item and the final answer is returned. A follow-up call on the same
 * memory id then proves that the exchange was stored in chat memory.
 */
public class ResponsesAiServiceToolsTest extends OpenAiBaseTest {

    @Singleton
    public static class Calculator {

        @Tool("Calculates the square root of a number")
        double squareRoot(double number) {
            return Math.sqrt(number);
        }
    }

    @RegisterAiService(tools = Calculator.class)
    interface Assistant {

        String chat(@MemoryId String memoryId, @UserMessage String message);
    }

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(Calculator.class, Assistant.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "my-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    private static final String SCENARIO = "tool-loop";
    private static final String TOOL_EXECUTED = "tool-executed";
    private static final String ANSWERED = "answered";

    @Inject
    Assistant assistant;

    @BeforeEach
    void setUp() {
        resetMappings();
        resetRequests();
    }

    @Test
    @ActivateRequestContext
    void toolLoopAndMemoryOverResponsesApi() throws IOException {
        wiremock().register(post(urlEqualTo("/v1/responses"))
                .inScenario(SCENARIO)
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo(TOOL_EXECUTED)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "resp_1",
                                  "model": "gpt-6-luna",
                                  "status": "completed",
                                  "output": [
                                    {
                                      "type": "function_call",
                                      "call_id": "call_1",
                                      "name": "squareRoot",
                                      "arguments": "{\\"number\\": 16}"
                                    }
                                  ],
                                  "usage": { "input_tokens": 1, "output_tokens": 2, "total_tokens": 3 }
                                }
                                """)));
        wiremock().register(post(urlEqualTo("/v1/responses"))
                .inScenario(SCENARIO)
                .whenScenarioStateIs(TOOL_EXECUTED)
                .willSetStateTo(ANSWERED)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(messageResponse("resp_2", "The square root of 16 is 4."))));
        wiremock().register(post(urlEqualTo("/v1/responses"))
                .inScenario(SCENARIO)
                .whenScenarioStateIs(ANSWERED)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(messageResponse("resp_3", "You're welcome."))));
        wiremock().setSingleScenarioState(SCENARIO, Scenario.STARTED);

        assertThat(assistant.chat("user-1", "What is the square root of 16?")).isEqualTo("The square root of 16 is 4.");
        assertThat(assistant.chat("user-1", "Thanks")).isEqualTo("You're welcome.");

        // serve events are newest first
        assertThat(wiremock().getServeEvents()).hasSize(3);
        Map<String, Object> firstRequest = getRequestAsMap(getRequestBody(wiremock().getServeEvents().get(2)));
        Map<String, Object> secondRequest = getRequestAsMap(getRequestBody(wiremock().getServeEvents().get(1)));
        Map<String, Object> thirdRequest = getRequestAsMap(getRequestBody(wiremock().getServeEvents().get(0)));

        // the tool is advertised using the flat Responses function format
        assertThat(items(firstRequest, "tools")).singleElement().satisfies(tool -> {
            assertThat(tool).containsEntry("type", "function").containsEntry("name", "squareRoot");
        });
        assertThat(items(firstRequest, "input")).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("role", "user");
            assertThat(text(item)).isEqualTo("What is the square root of 16?");
        });

        // the tool call and its result are replayed as function_call / function_call_output items
        List<Map<String, Object>> secondInput = items(secondRequest, "input");
        assertThat(secondInput).hasSize(3);
        assertThat(secondInput.get(0)).containsEntry("role", "user");
        assertThat(secondInput.get(1))
                .containsEntry("type", "function_call")
                .containsEntry("call_id", "call_1")
                .containsEntry("name", "squareRoot");
        assertThat(secondInput.get(2))
                .containsEntry("type", "function_call_output")
                .containsEntry("call_id", "call_1")
                .containsEntry("output", "4.0");

        // the follow-up call carries the whole previous exchange from chat memory
        List<Map<String, Object>> thirdInput = items(thirdRequest, "input");
        assertThat(thirdInput).extracting(item -> item.get("type"))
                .containsExactly("message", "function_call", "function_call_output", "message", "message");
        assertThat(thirdInput.get(3)).containsEntry("role", "assistant");
        assertThat(text(thirdInput.get(3))).isEqualTo("The square root of 16 is 4.");
        assertThat(thirdInput.get(4)).containsEntry("role", "user");
        assertThat(text(thirdInput.get(4))).isEqualTo("Thanks");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> request, String key) {
        return (List<Map<String, Object>>) request.get(key);
    }

    @SuppressWarnings("unchecked")
    private static String text(Map<String, Object> messageItem) {
        List<Map<String, Object>> content = (List<Map<String, Object>>) messageItem.get("content");
        return (String) content.get(0).get("text");
    }

    private static String messageResponse(String id, String text) {
        return """
                {
                  "id": "%s",
                  "model": "gpt-6-luna",
                  "status": "completed",
                  "output": [
                    {
                      "type": "message",
                      "role": "assistant",
                      "content": [ { "type": "output_text", "text": "%s" } ]
                    }
                  ],
                  "usage": { "input_tokens": 1, "output_tokens": 2, "total_tokens": 3 }
                }
                """.formatted(id, text);
    }
}
