package io.quarkiverse.langchain4j.mockapi.internal.dialect.anthropic;

import static io.quarkiverse.langchain4j.mockapi.internal.dialect.Json.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.FinishReason;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.Reply;
import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkiverse.langchain4j.mockapi.ToolCall;
import io.quarkiverse.langchain4j.mockapi.Usage;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Chunker;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Json;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.ChatRequestViewImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.MessageImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.ToolDefinitionImpl;

/**
 * Anthropic Messages API wire format ({@code POST /v1/messages}), following the public API reference:
 * <ul>
 * <li><a href="https://platform.claude.com/docs/en/api/messages">https://platform.claude.com/docs/en/api/messages</a>: request
 * body ({@code model}, {@code max_tokens},
 * {@code messages} with string or block content of type {@code text}, {@code tool_use} and {@code tool_result},
 * {@code system} as a string or {@code TextBlockParam}s, {@code tools} with {@code input_schema}, {@code tool_choice}
 * {@code auto}/{@code any}/{@code tool}/{@code none}, {@code temperature}, {@code top_p}, {@code stream}), the
 * {@code Message} object ({@code TextBlock}, {@code ThinkingBlock}, {@code ToolUseBlock}, {@code StopReason},
 * {@code Usage}) and the {@code RawMessageStreamEvent} types</li>
 * <li><a href=
 * "https://platform.claude.com/docs/en/build-with-claude/streaming">https://platform.claude.com/docs/en/build-with-claude/streaming</a>
 * (served for .../api/messages-streaming): the
 * event flow {@code message_start}, {@code content_block_start}/{@code content_block_delta}/{@code content_block_stop}
 * per block, {@code message_delta}, {@code message_stop}, with the SSE event name equal to the data's {@code type}, and
 * the {@code text_delta}, {@code input_json_delta}, {@code thinking_delta} and {@code signature_delta} deltas</li>
 * <li><a href="https://platform.claude.com/docs/en/api/errors">https://platform.claude.com/docs/en/api/errors</a>: the error
 * envelope
 * {@code {"type":"error","error":{"type","message"},"request_id"}} and the error type per HTTP status</li>
 * </ul>
 * {@code ChatReply.thinking} becomes a {@code thinking} content block with a placeholder {@code signature}. In the
 * dialect-neutral view, {@code system} becomes a {@code system} message and each {@code tool_result} block becomes a
 * {@code tool} message, as in the OpenAI view.
 */
public class AnthropicDialect implements Dialect {

    private static final String MESSAGES = "messages";
    private static final String SIGNATURE = "mock-signature";
    private static final Set<String> TEXT_BLOCKS = Set.of("text");

    private final ObjectMapper mapper = MockApiJson.mapper();

    @Override
    public String id() {
        return "anthropic";
    }

    @Override
    public String pathPrefix() {
        return "/anthropic";
    }

    @Override
    public Optional<Match> match(String method, String path, Map<String, List<String>> query, JsonNode bodyOrNull) {
        if (!"POST".equals(method) || !"/v1/messages".equals(path)) {
            return Optional.empty();
        }
        boolean streaming = bodyOrNull != null && bodyOrNull.path("stream").asBoolean(false);
        return Optional.of(new Match(Operation.CHAT, streaming, MESSAGES));
    }

    @Override
    public ChatRequestView parseChat(Match m, JsonNode body) {
        List<ChatRequestView.Message> messages = new ArrayList<>();
        JsonNode system = body.get("system");
        if (system != null && !system.isNull()) {
            messages.add(new MessageImpl("system", Json.contentText(system, TEXT_BLOCKS), null, null, null));
        }
        for (JsonNode msg : body.path("messages")) {
            parseMessage(msg, messages);
        }
        List<ChatRequestView.ToolDefinition> tools = new ArrayList<>();
        for (JsonNode tool : body.path("tools")) {
            tools.add(new ToolDefinitionImpl(text(tool, "name"), text(tool, "description"),
                    tool.hasNonNull("input_schema") ? tool.get("input_schema") : null));
        }
        return new ChatRequestViewImpl(
                text(body, "model"),
                body.path("stream").asBoolean(false),
                body.hasNonNull("temperature") ? body.get("temperature").asDouble() : null,
                body.hasNonNull("top_p") ? body.get("top_p").asDouble() : null,
                body.hasNonNull("max_tokens") ? body.get("max_tokens").asInt() : null,
                messages,
                tools,
                toolChoice(body.get("tool_choice")),
                null,
                null);
    }

    private static void parseMessage(JsonNode msg, List<ChatRequestView.Message> out) {
        String role = text(msg, "role");
        JsonNode content = msg.get("content");
        if (content == null || !content.isArray()) {
            out.add(new MessageImpl(role, content == null || content.isNull() ? null : content.asText(), null, null,
                    null));
            return;
        }
        StringBuilder text = null;
        List<ToolCall> toolCalls = null;
        for (JsonNode block : content) {
            switch (block.path("type").asText()) {
                case "text" -> {
                    text = text == null ? new StringBuilder() : text;
                    text.append(block.path("text").asText());
                }
                case "tool_use" -> {
                    toolCalls = toolCalls == null ? new ArrayList<>() : toolCalls;
                    toolCalls.add(ToolCall.of(text(block, "id"), text(block, "name"), block.path("input").toString()));
                }
                case "tool_result" -> out.add(new MessageImpl("tool", Json.contentText(block.get("content"), TEXT_BLOCKS), null,
                        text(block, "tool_use_id"), null));
                default -> {
                }
            }
        }
        if (text != null || toolCalls != null) {
            out.add(new MessageImpl(role, text == null ? null : text.toString(), toolCalls, null, null));
        }
    }

    /**
     * {@code tool} gives the tool's name, the other choices their {@code type}.
     */
    private static String toolChoice(JsonNode choice) {
        if (choice == null || choice.isNull()) {
            return null;
        }
        String type = text(choice, "type");
        return "tool".equals(type) ? text(choice, "name") : type;
    }

    @Override
    public Rendered render(Match m, Reply reply, CapturedRequest req) {
        if (reply instanceof ChatReply chat && m.operation() == Operation.CHAT) {
            return m.streaming() ? renderStream(chat, req) : renderMessage(chat, req);
        }
        throw UnrenderableReply.unsupported("Anthropic", m, reply);
    }

    private Rendered renderMessage(ChatReply reply, CapturedRequest req) {
        ObjectNode root = messageRoot(reply, req);
        ArrayNode content = root.putArray("content");
        if (reply.thinking() != null) {
            content.addObject().put("type", "thinking").put("thinking", reply.thinking()).put("signature", SIGNATURE);
        }
        List<ToolCall> toolCalls = toolCalls(reply);
        if (reply.text() != null || toolCalls.isEmpty()) {
            ObjectNode block = content.addObject().put("type", "text");
            block.putNull("citations");
            block.put("text", reply.text() == null ? "" : reply.text());
        }
        for (int i = 0; i < toolCalls.size(); i++) {
            ToolCall tc = toolCalls.get(i);
            content.addObject().put("type", "tool_use").put("id", toolUseId(tc, i)).put("name", tc.name())
                    .set("input", Json.argumentsObject(tc));
        }
        root.put("stop_reason", stopReason(reply.finishReason()));
        root.putNull("stop_sequence");
        usage(root, reply.usage());
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    private Rendered renderStream(ChatReply reply, CapturedRequest req) {
        List<SseEvent> events = new ArrayList<>();

        ObjectNode message = messageRoot(reply, req);
        message.putArray("content");
        message.putNull("stop_reason");
        message.putNull("stop_sequence");
        ObjectNode startUsage = usage(message, reply.usage());
        startUsage.put("output_tokens", 0);
        ObjectNode start = mapper.createObjectNode().put("type", "message_start");
        start.set("message", message);
        events.add(event(start));

        int index = 0;
        if (reply.thinking() != null) {
            events.add(blockStart(index, mapper.createObjectNode().put("type", "thinking").put("thinking", "")
                    .put("signature", "")));
            events.add(blockDelta(index, mapper.createObjectNode().put("type", "thinking_delta")
                    .put("thinking", reply.thinking())));
            events.add(blockDelta(index, mapper.createObjectNode().put("type", "signature_delta")
                    .put("signature", SIGNATURE)));
            events.add(blockStop(index));
            index++;
        }
        List<String> chunks = Chunker.chunks(reply);
        List<ToolCall> toolCalls = toolCalls(reply);
        if (!chunks.isEmpty() || toolCalls.isEmpty()) {
            events.add(blockStart(index, mapper.createObjectNode().put("type", "text").put("text", "")));
            for (String chunk : chunks.isEmpty() ? List.of("") : chunks) {
                events.add(blockDelta(index, mapper.createObjectNode().put("type", "text_delta").put("text", chunk)));
            }
            events.add(blockStop(index));
            index++;
        }
        for (int i = 0; i < toolCalls.size(); i++) {
            ToolCall tc = toolCalls.get(i);
            // the input is streamed as given, once it is known to be a JSON object
            Json.argumentsObject(tc);
            ObjectNode block = mapper.createObjectNode().put("type", "tool_use").put("id", toolUseId(tc, i))
                    .put("name", tc.name());
            block.putObject("input");
            events.add(blockStart(index, block));
            events.add(blockDelta(index, mapper.createObjectNode().put("type", "input_json_delta")
                    .put("partial_json", tc.argumentsJson())));
            events.add(blockStop(index));
            index++;
        }

        ObjectNode delta = mapper.createObjectNode().put("type", "message_delta");
        delta.putObject("delta").put("stop_reason", stopReason(reply.finishReason())).putNull("stop_sequence");
        delta.putObject("usage").put("output_tokens", reply.usage() == null ? 0 : reply.usage().outputTokens());
        events.add(event(delta));
        events.add(event(mapper.createObjectNode().put("type", "message_stop")));
        return Rendered.stream(Rendered.StreamFormat.SSE, events);
    }

    private ObjectNode messageRoot(ChatReply reply, CapturedRequest req) {
        ObjectNode root = mapper.createObjectNode();
        root.put("id", reply.id() != null ? reply.id() : "msg_mock_" + req.index());
        root.put("type", "message");
        root.put("role", "assistant");
        root.put("model", Json.modelName(reply, req));
        return root;
    }

    private static ObjectNode usage(ObjectNode root, Usage usage) {
        ObjectNode u = root.putObject("usage");
        u.put("input_tokens", usage == null ? 0 : usage.inputTokens());
        u.put("output_tokens", usage == null ? 0 : usage.outputTokens());
        u.put("cache_creation_input_tokens", usage == null ? null : usage.cacheCreationTokens());
        u.put("cache_read_input_tokens", usage == null ? null : usage.cacheReadTokens());
        return u;
    }

    private SseEvent blockStart(int index, ObjectNode block) {
        ObjectNode e = mapper.createObjectNode().put("type", "content_block_start").put("index", index);
        e.set("content_block", block);
        return event(e);
    }

    private SseEvent blockDelta(int index, ObjectNode delta) {
        ObjectNode e = mapper.createObjectNode().put("type", "content_block_delta").put("index", index);
        e.set("delta", delta);
        return event(e);
    }

    private SseEvent blockStop(int index) {
        return event(mapper.createObjectNode().put("type", "content_block_stop").put("index", index));
    }

    private static SseEvent event(ObjectNode data) {
        return SseEvent.of(data.path("type").asText(), data.toString());
    }

    private static List<ToolCall> toolCalls(ChatReply reply) {
        return reply.toolCalls() == null ? List.of() : reply.toolCalls();
    }

    private static String toolUseId(ToolCall tc, int index) {
        return tc.id() != null ? tc.id() : "toolu_mock_" + index;
    }

    private static String stopReason(FinishReason reason) {
        return switch (reason) {
            case STOP -> "end_turn";
            case TOOL_CALLS -> "tool_use";
            case LENGTH -> "max_tokens";
            case CONTENT_FILTER -> "refusal";
        };
    }

    @Override
    public Rendered renderError(Match m, int status, String message) {
        ObjectNode root = mapper.createObjectNode();
        root.put("type", "error");
        root.putObject("error").put("type", errorType(status)).put("message", message);
        root.put("request_id", "req_mock");
        return Rendered.json(status, MockApiJson.toBytes(root));
    }

    private static String errorType(int status) {
        return switch (status) {
            case 401 -> "authentication_error";
            case 402 -> "billing_error";
            case 403 -> "permission_error";
            case 404 -> "not_found_error";
            case 409 -> "conflict_error";
            case 413 -> "request_too_large";
            case 429 -> "rate_limit_error";
            case 504 -> "timeout_error";
            case 529 -> "overloaded_error";
            default -> status >= 500 ? "api_error" : "invalid_request_error";
        };
    }
}
