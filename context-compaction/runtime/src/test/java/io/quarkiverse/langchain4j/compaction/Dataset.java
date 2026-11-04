package io.quarkiverse.langchain4j.compaction;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The recorded support conversation of {@code datasets/compaction}: 126
 * messages, about 13.6k tokens, with large tool outputs and repeated polling calls.
 */
public final class Dataset {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Dataset() {
    }

    /**
     * @return the system message followed by the recorded conversation
     */
    public static List<ChatMessage> supportConversation() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(Fixtures.SYSTEM);
        for (String line : read("datasets/support-conversation.jsonl").split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode node = MAPPER.readTree(line);
            String text = node.path("text").asString();
            messages.add(switch (node.get("role").asString()) {
                case "user" -> UserMessage.from(text);
                case "assistant" -> AiMessage.from(text);
                case "tool_call" -> AiMessage.from(ToolExecutionRequest.builder().id(node.get("id").asString())
                        .name(node.get("name").asString()).arguments(node.get("arguments").asString()).build());
                case "tool_result" -> ToolExecutionResultMessage.from(node.get("id").asString(),
                        node.get("name").asString(), text);
                default -> throw new IllegalStateException("Unknown role in " + line);
            });
        }
        return messages;
    }

    private static String read(String resource) {
        try (InputStream in = Dataset.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
