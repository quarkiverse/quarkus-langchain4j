package io.quarkiverse.langchain4j.mockapi.internal.dialect.mistral;

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
import io.quarkiverse.langchain4j.mockapi.ModerationReply;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.Reply;
import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkiverse.langchain4j.mockapi.ToolCall;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Chunker;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.DerivedEmbeddings;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Json;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.ChatRequestViewImpl;

/**
 * Mistral AI wire format, following the published OpenAPI spec (mistralai/platform-docs-public {@code openapi.yaml},
 * rendered at <a href="https://docs.mistral.ai/api/">https://docs.mistral.ai/api/</a>):
 * <ul>
 * <li>chat ({@code chat_completion_v1_chat_completions_post}): request {@code ChatCompletionRequest} with
 * {@code SystemMessage}, {@code UserMessage}, {@code AssistantMessage} and {@code ToolMessage} (content a string or
 * {@code ContentChunk}s, of which {@code TextChunk}s are read), {@code Tool}/{@code Function}, {@code ToolChoice} or
 * {@code ToolChoiceEnum}, {@code ResponseFormat}/{@code JsonSchema}; response {@code ChatCompletionResponse},
 * {@code ChatCompletionChoice}, {@code AssistantMessage}, {@code ToolCall}, {@code FunctionCall} ({@code arguments} may
 * be an object or a string: a string is rendered), {@code UsageInfo}</li>
 * <li>streaming: the {@code stream} description ("data-only server-side events ... terminated by a data: [DONE]
 * message"), {@code CompletionEvent}, {@code CompletionChunk}, {@code CompletionResponseStreamChoice},
 * {@code DeltaMessage}; the last chunk carries the finish reason and the {@code usage}</li>
 * <li>embeddings ({@code embeddings_v1_embeddings_post}): {@code EmbeddingRequest}, {@code EmbeddingResponse},
 * {@code EmbeddingResponseData}</li>
 * <li>moderation ({@code moderations_v1_moderations_post}): {@code ClassificationRequest}, {@code ModerationResponse},
 * {@code ModerationObject} (one per input) with the categories of the documented example; a result has no
 * {@code flagged} field, so a {@link ModerationReply} only flags when it names one of these categories, for example
 * {@code violence_and_threats}</li>
 * <li>errors: {@code HTTPValidationError} ({@code detail[]} of {@code ValidationError}), the only error body the spec
 * documents for these operations (for 422); it is used for every status</li>
 * </ul>
 * The documented finish reasons are {@code stop}, {@code length}, {@code model_length}, {@code error} and
 * {@code tool_calls}; {@code CONTENT_FILTER} has no counterpart and becomes {@code error}. {@code ChatReply.thinking}
 * is not rendered.
 */
public class MistralDialect implements Dialect {

    private static final String CHAT_COMPLETIONS = "chat-completions";

    private static final List<String> MODERATION_CATEGORIES = List.of("sexual", "hate_and_discrimination",
            "violence_and_threats", "dangerous_and_criminal_content", "selfharm", "health", "financial", "law", "pii");

    private final ObjectMapper mapper = MockApiJson.mapper();

    @Override
    public String id() {
        return "mistralai";
    }

    @Override
    public String pathPrefix() {
        return "/mistralai";
    }

    @Override
    public Optional<Match> match(String method, String path, Map<String, List<String>> query, JsonNode bodyOrNull) {
        if (!"POST".equals(method)) {
            return Optional.empty();
        }
        return switch (path) {
            case "/v1/chat/completions" -> Optional.of(new Match(Operation.CHAT,
                    bodyOrNull != null && bodyOrNull.path("stream").asBoolean(false), CHAT_COMPLETIONS));
            case "/v1/embeddings" -> Optional.of(new Match(Operation.EMBEDDINGS, false, "embeddings"));
            case "/v1/moderations" -> Optional.of(new Match(Operation.MODERATION, false, "moderations"));
            default -> Optional.empty();
        };
    }

    @Override
    public ChatRequestView parseChat(Match m, JsonNode body) {
        List<ChatRequestView.Message> messages = new ArrayList<>();
        for (JsonNode msg : body.path("messages")) {
            messages.add(Json.chatCompletionsMessage(msg));
        }
        return new ChatRequestViewImpl(
                text(body, "model"),
                body.path("stream").asBoolean(false),
                body.hasNonNull("temperature") ? body.get("temperature").asDouble() : null,
                body.hasNonNull("top_p") ? body.get("top_p").asDouble() : null,
                body.hasNonNull("max_tokens") ? body.get("max_tokens").asInt() : null,
                messages,
                Json.functionTools(body.path("tools")),
                toolChoice(body.get("tool_choice")),
                text(body.path("response_format"), "type"),
                Json.responseSchema(body));
    }

    /**
     * A {@code ToolChoiceEnum} value as is, a {@code ToolChoice} as its function's name.
     */
    private static String toolChoice(JsonNode choice) {
        if (choice == null || choice.isNull()) {
            return null;
        }
        if (choice.isTextual()) {
            return choice.asText();
        }
        return text(choice.path("function"), "name");
    }

    @Override
    public Rendered render(Match m, Reply reply, CapturedRequest req) {
        if (reply instanceof ChatReply chat && m.operation() == Operation.CHAT) {
            return m.streaming() ? renderChatStream(chat, req) : renderChat(chat, req);
        }
        if (reply instanceof EmbeddingReply embeddings && m.operation() == Operation.EMBEDDINGS) {
            return renderEmbeddings(embeddings, req);
        }
        if (reply instanceof ModerationReply moderation && m.operation() == Operation.MODERATION) {
            return renderModeration(moderation, req);
        }
        throw UnrenderableReply.unsupported("Mistral", m, reply);
    }

    private Rendered renderChat(ChatReply reply, CapturedRequest req) {
        ObjectNode root = chatRoot(reply, req, "chat.completion");
        ObjectNode message = mapper.createObjectNode();
        message.put("role", "assistant");
        message.put("content", reply.text());
        List<ToolCall> toolCalls = toolCalls(reply);
        if (toolCalls.isEmpty()) {
            message.putNull("tool_calls");
        } else {
            addToolCalls(message, toolCalls);
        }
        message.put("prefix", false);

        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("index", 0);
        choice.set("message", message);
        choice.put("finish_reason", finishReason(reply.finishReason()));
        Json.chatUsage(root, reply.usage());
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    private Rendered renderChatStream(ChatReply reply, CapturedRequest req) {
        List<SseEvent> events = new ArrayList<>();
        boolean first = true;
        for (String piece : Chunker.chunks(reply)) {
            events.add(chunk(reply, req, delta(first).put("content", piece), null));
            first = false;
        }
        List<ToolCall> toolCalls = toolCalls(reply);
        if (!toolCalls.isEmpty()) {
            ObjectNode delta = delta(first);
            addToolCalls(delta, toolCalls);
            events.add(chunk(reply, req, delta, null));
            first = false;
        }
        if (first) {
            events.add(chunk(reply, req, delta(true).put("content", ""), null));
        }
        events.add(chunk(reply, req, mapper.createObjectNode(), reply));
        events.add(SseEvent.of(null, "[DONE]"));
        return Rendered.stream(Rendered.StreamFormat.SSE, events);
    }

    private ObjectNode delta(boolean first) {
        ObjectNode delta = mapper.createObjectNode();
        if (first) {
            delta.put("role", "assistant");
        }
        return delta;
    }

    /**
     * A {@code CompletionChunk}; {@code last} (the reply) adds the finish reason and the usage, otherwise null.
     */
    private SseEvent chunk(ChatReply reply, CapturedRequest req, ObjectNode delta, ChatReply last) {
        ObjectNode root = chatRoot(reply, req, "chat.completion.chunk");
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("index", 0);
        choice.set("delta", delta);
        if (last == null) {
            choice.putNull("finish_reason");
        } else {
            choice.put("finish_reason", finishReason(last.finishReason()));
            Json.chatUsage(root, last.usage());
        }
        return SseEvent.of(null, root.toString());
    }

    private ObjectNode chatRoot(ChatReply reply, CapturedRequest req, String object) {
        ObjectNode root = mapper.createObjectNode();
        root.put("id", reply.id() != null ? reply.id() : "cmpl-mock-" + req.index());
        root.put("object", object);
        root.put("created", Instant.now().getEpochSecond());
        root.put("model", Json.modelName(reply, req));
        return root;
    }

    private static void addToolCalls(ObjectNode message, List<ToolCall> toolCalls) {
        ArrayNode calls = message.putArray("tool_calls");
        for (int i = 0; i < toolCalls.size(); i++) {
            Json.functionCall(calls, toolCalls.get(i), i, true);
        }
    }

    private Rendered renderEmbeddings(EmbeddingReply reply, CapturedRequest req) {
        List<String> inputs = inputs(req);
        List<float[]> vectors = DerivedEmbeddings.vectors(reply, inputs);

        ObjectNode root = mapper.createObjectNode();
        root.put("id", "embd-mock-" + req.index());
        root.put("object", "list");
        root.put("model", Json.requestModel(req, "mock-embedding-model"));
        ArrayNode data = root.putArray("data");
        for (int i = 0; i < vectors.size(); i++) {
            ObjectNode e = data.addObject();
            e.put("object", "embedding");
            DerivedEmbeddings.floats(e.putArray("embedding"), vectors.get(i));
            e.put("index", i);
        }
        root.putObject("usage").put("prompt_tokens", inputs.size()).put("completion_tokens", 0)
                .put("total_tokens", inputs.size());
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    private Rendered renderModeration(ModerationReply reply, CapturedRequest req) {
        List<String> flagged = reply.categories() == null ? List.of() : reply.categories();
        List<String> names = new ArrayList<>(MODERATION_CATEGORIES);
        flagged.stream().filter(c -> !names.contains(c)).forEach(names::add);

        ObjectNode root = mapper.createObjectNode();
        root.put("id", "mod-mock-" + req.index());
        root.put("model", Json.requestModel(req, "mock-moderation-model"));
        ArrayNode results = root.putArray("results");
        for (int i = 0; i < Math.max(1, inputs(req).size()); i++) {
            ObjectNode result = results.addObject();
            ObjectNode categories = result.putObject("categories");
            ObjectNode scores = result.putObject("category_scores");
            for (String name : names) {
                boolean on = flagged.contains(name);
                categories.put(name, on);
                scores.put(name, on ? 1.0 : 0.0);
            }
        }
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    /**
     * The {@code input} of an embeddings or moderation request: a string or an array of strings.
     */
    private static List<String> inputs(CapturedRequest req) {
        return Json.strings(req.bodyAsJson().get("input"));
    }

    private static List<ToolCall> toolCalls(ChatReply reply) {
        return reply.toolCalls() == null ? List.of() : reply.toolCalls();
    }

    private static String finishReason(FinishReason reason) {
        return switch (reason) {
            case STOP -> "stop";
            case TOOL_CALLS -> "tool_calls";
            case LENGTH -> "length";
            case CONTENT_FILTER -> "error";
        };
    }

    @Override
    public Rendered renderError(Match m, int status, String message) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode detail = root.putArray("detail").addObject();
        detail.putArray("loc").add("body");
        detail.put("msg", message);
        detail.put("type", "mock_error");
        return Rendered.json(status, MockApiJson.toBytes(root));
    }
}
