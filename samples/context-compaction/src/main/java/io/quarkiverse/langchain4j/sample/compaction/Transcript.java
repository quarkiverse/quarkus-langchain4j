package io.quarkiverse.langchain4j.sample.compaction;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * The recorded conversation: a customer sorting out three orders with a support agent that calls tools returning
 * large payloads (126 messages, about 14k tokens). Plus questions about facts given early in the conversation.
 */
final class Transcript {

    record RecallQuestion(String question, String expected) {
    }

    static final List<RecallQuestion> QUESTIONS = List.of(
            new RecallQuestion("What is my customer id?", "CUST-48213"),
            new RecallQuestion("How do I want to be contacted?", "email only, never by phone"),
            new RecallQuestion("What is the tracking number of the burr grinder order?", "TRK-4X8LZ"),
            new RecallQuestion("Where will ORD-1002 be delivered?", "rue des Lilas"),
            new RecallQuestion("What is the refund id for the broken frother, and how much is it?", "RFD-5521, 49.90"),
            new RecallQuestion("Which order was already delivered?", "ORD-1001"));

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static List<ChatMessage> messages() {
        try (InputStream in = Transcript.class.getResourceAsStream("/support-conversation.jsonl")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isBlank())
                    .map(Transcript::parse)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ChatMessage parse(String line) {
        try {
            JsonNode node = MAPPER.readTree(line);
            String text = node.path("text").asText();
            return switch (node.get("role").asText()) {
                case "user" -> UserMessage.from(text);
                case "assistant" -> AiMessage.from(text);
                case "tool_call" -> AiMessage.from(ToolExecutionRequest.builder()
                        .id(node.get("id").asText()).name(node.get("name").asText())
                        .arguments(node.get("arguments").asText()).build());
                case "tool_result" -> ToolExecutionResultMessage.from(node.get("id").asText(),
                        node.get("name").asText(), text);
                default -> throw new IllegalStateException("Unknown role in " + line);
            };
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Transcript() {
    }
}
