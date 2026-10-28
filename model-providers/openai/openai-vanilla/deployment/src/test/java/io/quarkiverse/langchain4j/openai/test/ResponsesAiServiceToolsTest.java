package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
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

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Drives a full AI service tool loop over the Responses API: the model asks for a tool, the tool runs, the result is
 * sent back as a {@code function_call_output} item and the final answer is returned. A follow-up call on the same
 * memory id then proves that the exchange was stored in chat memory.
 */
public class ResponsesAiServiceToolsTest {

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
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "my-key");

    @Inject
    Assistant assistant;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setUp() {
        mock.reset();
    }

    @Test
    @ActivateRequestContext
    void toolLoopAndMemoryOverResponsesApi() {
        mock.chat().reply(ChatReply.toolCall("squareRoot", Map.of("number", 16)),
                ChatReply.text("The square root of 16 is 4."),
                ChatReply.text("You're welcome."));

        assertThat(assistant.chat("user-1", "What is the square root of 16?")).isEqualTo("The square root of 16 is 4.");
        assertThat(assistant.chat("user-1", "Thanks")).isEqualTo("You're welcome.");

        List<CapturedRequest> requests = mock.chat().requests();
        assertThat(requests).hasSize(3).allSatisfy(request -> assertThat(request.path()).isEqualTo("/v1/responses"));
        JsonNode firstRequest = requests.get(0).bodyAsJson();
        JsonNode secondRequest = requests.get(1).bodyAsJson();
        JsonNode thirdRequest = requests.get(2).bodyAsJson();

        // the tool is advertised using the flat Responses function format
        assertThat(firstRequest.get("tools").size()).isEqualTo(1);
        assertThat(firstRequest.get("tools").get(0).path("type").textValue()).isEqualTo("function");
        assertThat(firstRequest.get("tools").get(0).path("name").textValue()).isEqualTo("squareRoot");
        assertThat(firstRequest.get("input").size()).isEqualTo(1);
        assertThat(firstRequest.get("input").get(0).path("role").textValue()).isEqualTo("user");
        assertThat(text(firstRequest.get("input").get(0))).isEqualTo("What is the square root of 16?");

        // the tool call and its result are replayed as function_call / function_call_output items; call_mock_0 is the id
        // the mock gives the first tool call of a reply
        JsonNode secondInput = secondRequest.get("input");
        assertThat(secondInput.size()).isEqualTo(3);
        assertThat(secondInput.get(0).path("role").textValue()).isEqualTo("user");
        assertThat(secondInput.get(1).path("type").textValue()).isEqualTo("function_call");
        assertThat(secondInput.get(1).path("call_id").textValue()).isEqualTo("call_mock_0");
        assertThat(secondInput.get(1).path("name").textValue()).isEqualTo("squareRoot");
        assertThat(secondInput.get(2).path("type").textValue()).isEqualTo("function_call_output");
        assertThat(secondInput.get(2).path("call_id").textValue()).isEqualTo("call_mock_0");
        assertThat(secondInput.get(2).path("output").textValue()).isEqualTo("4.0");

        // the follow-up call carries the whole previous exchange from chat memory
        JsonNode thirdInput = thirdRequest.get("input");
        assertThat(types(thirdInput))
                .containsExactly("message", "function_call", "function_call_output", "message", "message");
        assertThat(thirdInput.get(3).path("role").textValue()).isEqualTo("assistant");
        assertThat(text(thirdInput.get(3))).isEqualTo("The square root of 16 is 4.");
        assertThat(thirdInput.get(4).path("role").textValue()).isEqualTo("user");
        assertThat(text(thirdInput.get(4))).isEqualTo("Thanks");
    }

    private static List<String> types(JsonNode items) {
        List<String> types = new ArrayList<>();
        items.forEach(item -> types.add(item.path("type").textValue()));
        return types;
    }

    private static String text(JsonNode messageItem) {
        return messageItem.get("content").get(0).path("text").textValue();
    }
}
