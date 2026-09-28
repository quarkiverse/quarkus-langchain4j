package io.quarkiverse.langchain4j.gpullama3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.beehive.jitllm.api.ChatContent;
import org.beehive.jitllm.api.ChatRole;
import org.beehive.jitllm.api.ToolSpec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.JsonSchemaElementUtils;

/**
 * Pure conversions between LangChain4j's vocabulary and the engine's façade.
 *
 * <p>
 * Separate from the model beans so the mapping can be tested <b>without a model</b>. What a small
 * model chooses to emit is its own business; whether a tool call is transported and mapped correctly
 * is this extension's, and the two should not be provable only together.
 *
 * <p>
 * Package-private: a test seam, not API.
 */
final class GPULlama3Conversions {

    /**
     * Plain Jackson, not {@code dev.langchain4j.internal.Json}.
     *
     * <p>
     * That indirection resolves to Quarkus's codec factory, which needs the CDI container — so a
     * conversion using it is not a pure function and cannot be tested without booting Quarkus. These
     * conversions are the part of the extension that should be provable on its own.
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    private GPULlama3Conversions() {
    }

    /**
     * The conversation, in the engine's vocabulary.
     *
     * <p>
     * Where tool definitions go, which stop tokens apply, how a family opens a turn and how the
     * reasoning control is encoded are all the engine's now. This extension used to reproduce the
     * Llama 3.2 template by hand; it no longer has an opinion about any of it.
     */
    static List<org.beehive.jitllm.api.ChatMessage> toEngineMessages(List<ChatMessage> messages) {
        List<org.beehive.jitllm.api.ChatMessage> converted = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage user) {
                converted.add(org.beehive.jitllm.api.ChatMessage.of(ChatRole.USER, user.singleText()));
            } else if (message instanceof SystemMessage system) {
                converted.add(org.beehive.jitllm.api.ChatMessage.of(ChatRole.SYSTEM, system.text()));
            } else if (message instanceof AiMessage ai) {
                converted.add(toAssistantMessage(ai));
            } else if (message instanceof ToolExecutionResultMessage toolResult) {
                converted.add(new org.beehive.jitllm.api.ChatMessage(
                        ChatRole.TOOL,
                        List.of(new ChatContent.ToolResult(
                                blankToGenerated(toolResult.id()),
                                toolResult.toolName(),
                                unwrapToolResult(toolResult.text())))));
            }
        }
        return converted;
    }

    static org.beehive.jitllm.api.ChatMessage toAssistantMessage(AiMessage ai) {
        List<ChatContent> content = new ArrayList<>();
        if (ai.text() != null && !ai.text().isEmpty()) {
            content.add(new ChatContent.Text(ai.text()));
        }
        if (ai.hasToolExecutionRequests()) {
            for (ToolExecutionRequest tool : ai.toolExecutionRequests()) {
                content.add(new ChatContent.ToolCall(
                        blankToGenerated(tool.id()),
                        tool.name(),
                        tool.arguments() == null || tool.arguments().isBlank() ? "{}" : tool.arguments()));
            }
        }
        if (content.isEmpty()) {
            content.add(new ChatContent.Text(""));
        }
        return new org.beehive.jitllm.api.ChatMessage(ChatRole.ASSISTANT, content);
    }

    /** Tool specifications, serialized exactly as this extension serialized them before. */
    static List<ToolSpec> toEngineTools(List<ToolSpecification> tools) {
        List<ToolSpec> specs = new ArrayList<>(tools.size());
        for (ToolSpecification tool : tools) {
            Map<String, Object> parameters = tool.parameters() == null
                    ? Map.of("type", "object", "properties", Map.of())
                    : JsonSchemaElementUtils.toMap(tool.parameters());
            specs.add(new ToolSpec(
                    tool.name(),
                    tool.description() == null ? "" : tool.description(),
                    writeJson(parameters)));
        }
        return specs;
    }

    /** The engine's tool calls, as LangChain4j execution requests — order preserved. */
    static List<ToolExecutionRequest> toToolExecutionRequests(List<ChatContent.ToolCall> calls) {
        List<ToolExecutionRequest> requests = new ArrayList<>(calls.size());
        for (ChatContent.ToolCall call : calls) {
            requests.add(ToolExecutionRequest.builder()
                    .id(call.id())
                    .name(call.name())
                    .arguments(normalizeJson(call.argumentsJson()))
                    .build());
        }
        return requests;
    }

    /**
     * The engine's stop reason, in LangChain4j's vocabulary.
     *
     * <p>
     * Extracted calls do not override the reason: a response that ran out of budget mid-call
     * stays {@code LENGTH}, because telling a caller to execute a call the model had not finished
     * writing is worse than telling them it was truncated.
     */
    static dev.langchain4j.model.output.FinishReason toLangChain4jFinishReason(
            org.beehive.jitllm.api.FinishReason engineReason) {
        return switch (engineReason) {
            case TOOL_CALL -> dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
            case MAX_TOKENS, CONTEXT_FULL -> dev.langchain4j.model.output.FinishReason.LENGTH;
            case STOP_TOKEN, STOP_SEQUENCE -> dev.langchain4j.model.output.FinishReason.STOP;
            // jitllm 1.0.1+: the request's CancellationToken stopped generation. LangChain4j has no
            // cancelled reason, and this extension never cancels a request itself.
            case CANCELLED -> dev.langchain4j.model.output.FinishReason.OTHER;
        };
    }

    /** Prompt tokens are what this turn ingested: a reused conversation prefix is not counted again. */
    static dev.langchain4j.model.output.TokenUsage toTokenUsage(org.beehive.jitllm.api.GenerationResult result) {
        return new dev.langchain4j.model.output.TokenUsage(result.promptTokens(), result.generatedTokens());
    }

    static String unwrapToolResult(String text) {
        if (text == null) {
            return "";
        }
        if (text.startsWith("\"")) {
            try {
                return JSON.readValue(text, String.class);
            } catch (JsonProcessingException ignored) {
                // Not a JSON string literal; pass it through unchanged.
            }
        }
        return text;
    }

    /** Reformats a JSON string canonically, or returns it unchanged when it is not JSON. */
    static String normalizeJson(String json) {
        try {
            return JSON.writeValueAsString(JSON.readValue(json, Object.class));
        } catch (JsonProcessingException ignored) {
            return json;
        }
    }

    private static String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("a tool's parameter schema could not be serialized", e);
        }
    }

    /**
     * The façade requires a non-blank id so a result can be matched back to a call; LangChain4j
     * allows none.
     */
    static String blankToGenerated(String id) {
        return id == null || id.isBlank() ? generateCallId() : id;
    }

    /** An Ollama-style tool call id: {@code call_} plus 8 alphanumeric characters. */
    static String generateCallId() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder id = new StringBuilder("call_");
        for (int i = 0; i < 8; i++) {
            id.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }
        return id.toString();
    }
}
