package io.quarkiverse.langchain4j.mockapi.internal.dialect.ollama;

import static io.quarkiverse.langchain4j.mockapi.internal.dialect.Json.text;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.FinishReason;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.Reply;
import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkiverse.langchain4j.mockapi.ToolCall;
import io.quarkiverse.langchain4j.mockapi.Usage;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Chunker;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.DerivedEmbeddings;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Json;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.ChatRequestViewImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.MessageImpl;

/**
 * Ollama wire format, following the Ollama API docs (ollama/ollama {@code docs/api.md}, sections "Generate a chat
 * completion" and "Generate Embeddings", and {@code docs/openapi.yaml}, schemas {@code ChatRequest},
 * {@code ChatMessage}, {@code ToolDefinition}, {@code ToolCall}, {@code ChatResponse}, {@code ChatStreamEvent},
 * {@code EmbedRequest}, {@code EmbedResponse}, {@code ErrorResponse}), plus {@code docs/api/streaming.mdx},
 * {@code docs/api/usage.mdx} and {@code docs/api/errors.mdx}:
 * <ul>
 * <li>chat ({@code POST /api/chat}): {@code stream} defaults to {@code true}; {@code options.temperature},
 * {@code options.top_p} and {@code options.num_predict} are read, {@code format} is {@code json} (view type
 * {@code json}) or a JSON schema (view type {@code json_schema}); a tool message's {@code tool_name} is the view's
 * name. Tool calls have no id and their {@code arguments} is a JSON object. The response is one object with
 * {@code message}, {@code done: true}, {@code done_reason}, the token counts {@code prompt_eval_count} and
 * {@code eval_count} and the durations (rendered as 0)</li>
 * <li>streaming: newline-delimited JSON objects ({@code application/x-ndjson}), each with a partial
 * {@code message.content} and {@code done: false}, then a final object with an empty content, {@code done: true},
 * the {@code done_reason}, counts and durations. Tool calls are sent in one object, as in the documented
 * example</li>
 * <li>embeddings ({@code POST /api/embed}): {@code input} a string or an array of strings; response
 * {@code model}, {@code embeddings}, durations and {@code prompt_eval_count}</li>
 * <li>errors: {@code {"error": "..."}}</li>
 * </ul>
 * The documented tool call examples finish with {@code done_reason} {@code stop}, so {@code STOP} and
 * {@code TOOL_CALLS} both render {@code stop}. {@code done_reason} is a free-form string and only {@code stop},
 * {@code load} and {@code unload} appear in the docs: {@code LENGTH} renders {@code length} (what the langchain4j
 * client maps to {@code LENGTH}, though only on its blocking path: the Quarkus streaming client ignores
 * {@code done_reason}) and {@code CONTENT_FILTER} renders {@code content_filter}, which is not a documented value.
 */
public class OllamaDialect implements Dialect {

    private final ObjectMapper mapper = MockApiJson.mapper();

    @Override
    public String id() {
        return "ollama";
    }

    @Override
    public String pathPrefix() {
        return "/ollama";
    }

    @Override
    public Optional<Match> match(String method, String path, Map<String, List<String>> query, JsonNode bodyOrNull) {
        if (!"POST".equals(method)) {
            return Optional.empty();
        }
        return switch (path) {
            case "/api/chat" -> Optional.of(new Match(Operation.CHAT, bodyOrNull == null || streaming(bodyOrNull), "chat"));
            case "/api/embed" -> Optional.of(new Match(Operation.EMBEDDINGS, false, "embed"));
            default -> Optional.empty();
        };
    }

    /**
     * {@code stream} defaults to {@code true}.
     */
    private static boolean streaming(JsonNode body) {
        JsonNode stream = body.get("stream");
        return stream == null || stream.isNull() || stream.asBoolean();
    }

    @Override
    public ChatRequestView parseChat(Match m, JsonNode body) {
        List<ChatRequestView.Message> messages = new ArrayList<>();
        for (JsonNode msg : body.path("messages")) {
            messages.add(parseMessage(msg));
        }
        JsonNode options = body.path("options");
        JsonNode format = body.get("format");
        boolean schema = format != null && format.isObject();
        return new ChatRequestViewImpl(
                text(body, "model"),
                streaming(body),
                options.hasNonNull("temperature") ? options.get("temperature").asDouble() : null,
                options.hasNonNull("top_p") ? options.get("top_p").asDouble() : null,
                options.hasNonNull("num_predict") ? options.get("num_predict").asInt() : null,
                messages,
                Json.functionTools(body.path("tools")),
                null,
                schema ? "json_schema" : text(body, "format"),
                schema ? format : null);
    }

    private static ChatRequestView.Message parseMessage(JsonNode msg) {
        List<ToolCall> toolCalls = null;
        if (msg.hasNonNull("tool_calls")) {
            toolCalls = new ArrayList<>();
            for (JsonNode tc : msg.get("tool_calls")) {
                JsonNode fn = tc.path("function");
                toolCalls.add(ToolCall.of(text(fn, "name"), Json.jsonText(fn.get("arguments"))));
            }
        }
        return new MessageImpl(text(msg, "role"), text(msg, "content"), toolCalls, null, text(msg, "tool_name"));
    }

    @Override
    public Rendered render(Match m, Reply reply, CapturedRequest req) {
        if (reply instanceof ChatReply chat && m.operation() == Operation.CHAT) {
            return m.streaming() ? renderChatStream(chat, req) : renderChat(chat, req);
        }
        if (reply instanceof EmbeddingReply embeddings && m.operation() == Operation.EMBEDDINGS) {
            return renderEmbeddings(embeddings, req);
        }
        throw UnrenderableReply.unsupported("Ollama", m, reply);
    }

    /**
     * The reply's tool calls, each with its arguments as a JSON object ({@code {}} when absent).
     */
    private List<ObjectNode> toolCalls(ChatReply reply) {
        List<ObjectNode> toolCalls = new ArrayList<>();
        for (ToolCall tc : reply.toolCalls() == null ? List.<ToolCall> of() : reply.toolCalls()) {
            ObjectNode call = mapper.createObjectNode();
            call.putObject("function").put("name", tc.name()).set("arguments", Json.argumentsObject(tc));
            toolCalls.add(call);
        }
        return toolCalls;
    }

    private Rendered renderChat(ChatReply reply, CapturedRequest req) {
        ObjectNode message = message(reply.text());
        if (reply.thinking() != null) {
            message.put("thinking", reply.thinking());
        }
        List<ObjectNode> toolCalls = toolCalls(reply);
        if (!toolCalls.isEmpty()) {
            message.putArray("tool_calls").addAll(toolCalls);
        }
        return Rendered.json(200, MockApiJson.toBytes(done(reply, req, message)));
    }

    private Rendered renderChatStream(ChatReply reply, CapturedRequest req) {
        List<ObjectNode> toolCalls = toolCalls(reply);
        List<SseEvent> events = new ArrayList<>();
        if (reply.thinking() != null) {
            events.add(event(partial(reply, req, message("").put("thinking", reply.thinking()))));
        }
        for (String piece : Chunker.chunks(reply)) {
            events.add(event(partial(reply, req, message(piece))));
        }
        if (!toolCalls.isEmpty()) {
            ObjectNode message = message("");
            message.putArray("tool_calls").addAll(toolCalls);
            events.add(event(partial(reply, req, message)));
        }
        events.add(event(done(reply, req, message(""))));
        return Rendered.stream(Rendered.StreamFormat.NDJSON, events);
    }

    private static SseEvent event(ObjectNode json) {
        return SseEvent.of(null, json.toString());
    }

    private ObjectNode message(String content) {
        ObjectNode message = mapper.createObjectNode();
        message.put("role", "assistant");
        message.put("content", content == null ? "" : content);
        return message;
    }

    private ObjectNode partial(ChatReply reply, CapturedRequest req, ObjectNode message) {
        ObjectNode root = root(reply, req);
        root.set("message", message);
        root.put("done", false);
        return root;
    }

    /**
     * The final object: the message, {@code done}, {@code done_reason}, counts and durations.
     */
    private ObjectNode done(ChatReply reply, CapturedRequest req, ObjectNode message) {
        Usage usage = reply.usage();
        ObjectNode root = root(reply, req);
        root.set("message", message);
        root.put("done", true);
        root.put("done_reason", doneReason(reply.finishReason()));
        // DIVERGENCE: the Quarkus streaming client reads stream objects without its snake_case mapper, so it misses
        // these documented counts (it looks for promptEvalCount and evalCount); the blocking client reads them
        root.put("total_duration", 0L);
        root.put("load_duration", 0L);
        root.put("prompt_eval_count", usage == null ? 0 : usage.inputTokens());
        root.put("prompt_eval_duration", 0L);
        root.put("eval_count", usage == null ? 0 : usage.outputTokens());
        root.put("eval_duration", 0L);
        return root;
    }

    private ObjectNode root(ChatReply reply, CapturedRequest req) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", Json.modelName(reply, req));
        root.put("created_at", Instant.now().toString());
        return root;
    }

    private static String doneReason(FinishReason reason) {
        return switch (reason) {
            case STOP, TOOL_CALLS -> "stop";
            case LENGTH -> "length";
            case CONTENT_FILTER -> "content_filter";
        };
    }

    private Rendered renderEmbeddings(EmbeddingReply reply, CapturedRequest req) {
        List<String> inputs = Json.strings(req.bodyAsJson().get("input"));
        List<float[]> vectors = DerivedEmbeddings.vectors(reply, inputs);

        ObjectNode root = mapper.createObjectNode();
        root.put("model", Json.requestModel(req, "mock-embedding-model"));
        ArrayNode embeddings = root.putArray("embeddings");
        for (float[] vector : vectors) {
            DerivedEmbeddings.floats(embeddings.addArray(), vector);
        }
        root.put("total_duration", 0L);
        root.put("load_duration", 0L);
        root.put("prompt_eval_count", inputs.size());
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    @Override
    public Rendered renderError(Match m, int status, String message) {
        ObjectNode root = mapper.createObjectNode();
        root.put("error", message);
        return Rendered.json(status, MockApiJson.toBytes(root));
    }
}
