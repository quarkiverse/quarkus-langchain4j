package org.acme.examples.aiservices;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Assertions on the JSON body of an OpenAI chat completions request captured by the mock model API.
 */
public final class OpenAiRequestAssertions {

    private OpenAiRequestAssertions() {
    }

    public static void assertSingleRequestMessage(JsonNode request, String value) {
        assertMultipleRequestMessage(request, List.of(new MessageContent("user", value)));
    }

    /**
     * A {@code null} content accepts a message whose content is absent or {@code null}.
     */
    public static void assertMultipleRequestMessage(JsonNode request, List<MessageContent> messageContents) {
        JsonNode messages = request.get("messages");
        assertThat(messages).hasSize(messageContents.size());
        for (int i = 0; i < messageContents.size(); i++) {
            MessageContent expected = messageContents.get(i);
            JsonNode message = messages.get(i);
            assertThat(message.path("role").textValue()).isEqualTo(expected.role());
            if (expected.content() == null) {
                if (message.has("content")) {
                    assertThat(message.get("content").isNull()).isTrue();
                }
            } else {
                assertThat(message.path("content").textValue()).isEqualTo(expected.content());
            }
        }
    }

    public static void assertSingleFunction(JsonNode request, String functionName) {
        JsonNode tools = request.get("tools");
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).path("type").textValue()).isEqualTo("function");
        assertThat(tools.get(0).path("function").path("name").textValue()).isEqualTo(functionName);
    }

    public record MessageContent(String role, String content) {
    }
}
