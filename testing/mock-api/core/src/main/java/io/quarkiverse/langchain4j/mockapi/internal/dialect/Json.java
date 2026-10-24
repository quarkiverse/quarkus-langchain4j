package io.quarkiverse.langchain4j.mockapi.internal.dialect;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.ToolCall;
import io.quarkiverse.langchain4j.mockapi.Usage;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.MessageImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.ToolDefinitionImpl;

/**
 * JSON reading and writing that several dialects share because their providers document the same shapes:
 * <ul>
 * <li>the chat completions shapes that OpenAI defines and Mistral and watsonx.ai document with the same field names: a
 * message ({@code role}, {@code content} as a string or a list of {@code text} parts, {@code tool_calls} of
 * {@code {id, type: function, function{name, arguments}}}, {@code tool_call_id}, {@code name}), the
 * {@code tools[].function{name, description, parameters}} definitions (also Ollama's), the
 * {@code response_format.json_schema.schema}, and the {@code usage} with {@code prompt_tokens},
 * {@code completion_tokens} and {@code total_tokens};</li>
 * <li>content given as a string or as a list of typed parts, of which the parts of the given types contribute their
 * {@code text} (the OpenAI chat completions, Responses and Decisions parts, the Mistral chunks, the watsonx.ai parts,
 * the Anthropic blocks);</li>
 * <li>an embeddings {@code input} given as a string or an array of strings (OpenAI, Mistral, Ollama, and the Hugging
 * Face {@code inputs});</li>
 * <li>tool call arguments as the JSON object that Anthropic ({@code input}), Gemini ({@code args}) and Ollama
 * ({@code arguments}) document, or as any JSON document (the Bedrock {@code Document}).</li>
 * </ul>
 */
public final class Json {

    private static final Set<String> TEXT_PARTS = Set.of("text");

    private Json() {
    }

    /**
     * The field as text, or null when it is absent or JSON null.
     */
    public static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /**
     * A string's text, other JSON as JSON, null when absent.
     */
    public static String jsonText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.isTextual() ? node.asText() : node.toString();
    }

    /**
     * A string, or the concatenated {@code text} of every content part whose {@code type} is one of
     * {@code partTypes}; null when absent.
     */
    public static String contentText(JsonNode content, Set<String> partTypes) {
        if (content == null || content.isNull()) {
            return null;
        }
        if (content.isTextual()) {
            return content.asText();
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : content) {
            if (partTypes.contains(part.path("type").asText()) && part.hasNonNull("text")) {
                sb.append(part.get("text").asText());
            }
        }
        return sb.toString();
    }

    /**
     * A string as one element, an array as its elements (a string's text, other JSON as JSON), absent as none.
     */
    public static List<String> strings(JsonNode node) {
        List<String> strings = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(i -> strings.add(i.isTextual() ? i.asText() : i.toString()));
        } else if (node != null && !node.isNull() && !node.isMissingNode()) {
            strings.add(node.asText());
        }
        return strings;
    }

    /**
     * The given text field of the request body, or {@code fallback} when it is absent or the body is not JSON.
     */
    public static String requestText(CapturedRequest req, String field, String fallback) {
        try {
            JsonNode body = req.bodyAsJson();
            return body.hasNonNull(field) ? body.get(field).asText() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /**
     * The {@code model} of the request body, or {@code fallback} when it is absent or the body is not JSON.
     */
    public static String requestModel(CapturedRequest req, String fallback) {
        return requestText(req, "model", fallback);
    }

    /**
     * The reply's model name, else the request's {@code model}, else {@code mock-model}.
     */
    public static String modelName(ChatReply reply, CapturedRequest req) {
        return reply.modelName() != null ? reply.modelName() : requestModel(req, "mock-model");
    }

    /**
     * The tool call's arguments as a JSON document; blank arguments give an empty object.
     *
     * @throws UnrenderableReply naming the tool when the arguments are not valid JSON
     */
    public static JsonNode arguments(ToolCall tc) {
        String args = tc.argumentsJson();
        if (args == null || args.isBlank()) {
            return MockApiJson.mapper().createObjectNode();
        }
        try {
            return MockApiJson.mapper().readTree(args);
        } catch (JsonProcessingException e) {
            throw new UnrenderableReply("Tool call " + tc.name() + " arguments are not valid JSON: " + args);
        }
    }

    /**
     * The tool call's arguments as a JSON object; blank arguments give an empty object.
     *
     * @throws UnrenderableReply naming the tool when the arguments are not a JSON object
     */
    public static JsonNode argumentsObject(ToolCall tc) {
        JsonNode args = arguments(tc);
        if (!args.isObject()) {
            throw new UnrenderableReply(
                    "Tool call " + tc.name() + " arguments must be a JSON object, got: " + tc.argumentsJson());
        }
        return args;
    }

    /**
     * A chat completions message.
     */
    public static ChatRequestView.Message chatCompletionsMessage(JsonNode msg) {
        List<ToolCall> toolCalls = null;
        if (msg.hasNonNull("tool_calls")) {
            toolCalls = new ArrayList<>();
            for (JsonNode tc : msg.get("tool_calls")) {
                JsonNode fn = tc.path("function");
                toolCalls.add(ToolCall.of(text(tc, "id"), text(fn, "name"), jsonText(fn.get("arguments"))));
            }
        }
        return new MessageImpl(text(msg, "role"), contentText(msg.get("content"), TEXT_PARTS), toolCalls,
                text(msg, "tool_call_id"), text(msg, "name"));
    }

    /**
     * The {@code tools[].function} definitions.
     */
    public static List<ChatRequestView.ToolDefinition> functionTools(JsonNode tools) {
        List<ChatRequestView.ToolDefinition> result = new ArrayList<>();
        for (JsonNode tool : tools) {
            JsonNode fn = tool.path("function");
            result.add(new ToolDefinitionImpl(text(fn, "name"), text(fn, "description"),
                    fn.hasNonNull("parameters") ? fn.get("parameters") : null));
        }
        return result;
    }

    /**
     * The {@code response_format.json_schema.schema} of the request body, or null.
     */
    public static JsonNode responseSchema(JsonNode body) {
        JsonNode schema = body.path("response_format").path("json_schema").path("schema");
        return schema.isMissingNode() || schema.isNull() ? null : schema;
    }

    /**
     * Adds a chat completions tool call {@code {id, type: function, function{name, arguments}}}, preceded by its
     * {@code index} when {@code withIndex}; the id is the tool call's, else {@code call_mock_<index>}.
     */
    public static void functionCall(ArrayNode calls, ToolCall tc, int index, boolean withIndex) {
        ObjectNode call = calls.addObject();
        if (withIndex) {
            call.put("index", index);
        }
        call.put("id", callId(tc, index));
        call.put("type", "function");
        call.putObject("function").put("name", tc.name()).put("arguments", tc.argumentsJson());
    }

    /**
     * The tool call's id, else {@code call_mock_<index>}.
     */
    public static String callId(ToolCall tc, int index) {
        return tc.id() != null ? tc.id() : "call_mock_" + index;
    }

    /**
     * The chat completions {@code usage}.
     */
    public static void chatUsage(ObjectNode root, Usage usage) {
        int in = usage == null ? 0 : usage.inputTokens();
        int out = usage == null ? 0 : usage.outputTokens();
        root.putObject("usage").put("prompt_tokens", in).put("completion_tokens", out).put("total_tokens", in + out);
    }
}
