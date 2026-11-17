package io.quarkiverse.langchain4j.testing.evaluation.compaction;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A recorded conversation and the questions to ask at its end.
 *
 * @param timestamps aligned with {@code messages}, {@code null} when a message has no recorded time
 */
public record Scenario(String name, List<ChatMessage> messages, List<Instant> timestamps,
        List<RecallQuestion> questions) {

    /**
     * @param expected facts a correct answer states; may be empty
     */
    public record RecallQuestion(String question, List<String> expected) {
    }

    private static final ObjectMapper MAPPER = QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER;

    public static Scenario load(String conversation, String questions) {
        List<ChatMessage> messages = new ArrayList<>();
        List<Instant> timestamps = new ArrayList<>();
        int number = 0;
        for (String line : resource(conversation).split("\n")) {
            number++;
            if (line.isBlank()) {
                continue;
            }
            try {
                JsonNode node = MAPPER.readTree(line);
                messages.add(message(node));
                timestamps.add(node.hasNonNull("ts") ? Instant.parse(node.get("ts").asString()) : null);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(conversation + ":" + number + ": " + e.getMessage(), e);
            }
        }
        List<RecallQuestion> recall = new ArrayList<>();
        for (JsonNode node : MAPPER.readTree(resource(questions))) {
            List<String> expected = new ArrayList<>();
            node.path("expected").forEach(e -> expected.add(e.asString()));
            recall.add(new RecallQuestion(node.get("question").asString(), List.copyOf(expected)));
        }
        return new Scenario(conversation, List.copyOf(messages), timestamps, List.copyOf(recall));
    }

    private static ChatMessage message(JsonNode node) {
        String text = node.path("text").asString();
        return switch (node.get("role").asString()) {
            case "system" -> SystemMessage.from(text);
            case "user" -> UserMessage.from(text);
            case "assistant" -> AiMessage.from(text);
            case "tool_call" -> AiMessage.from(ToolExecutionRequest.builder().id(node.path("id").asString(null))
                    .name(node.get("name").asString()).arguments(node.path("arguments").asString("{}")).build());
            case "tool_result" -> ToolExecutionResultMessage.from(node.path("id").asString(null),
                    node.get("name").asString(), text);
            default -> throw new IllegalArgumentException("unknown role " + node.get("role"));
        };
    }

    private static String resource(String path) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing classpath resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
