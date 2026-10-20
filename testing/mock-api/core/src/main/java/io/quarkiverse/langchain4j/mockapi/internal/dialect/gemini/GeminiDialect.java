package io.quarkiverse.langchain4j.mockapi.internal.dialect.gemini;

import static io.quarkiverse.langchain4j.mockapi.internal.dialect.Json.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import io.quarkiverse.langchain4j.mockapi.internal.model.ToolDefinitionImpl;

/**
 * Gemini API wire format ({@code POST /v1beta/models/{model}:generateContent}, {@code :streamGenerateContent?alt=sse},
 * {@code :embedContent} and {@code :batchEmbedContents}), following the public API reference:
 * <ul>
 * <li><a href="https://ai.google.dev/api/generate-content">https://ai.google.dev/api/generate-content</a>: the request body
 * ({@code contents} of {@code Content} with role
 * {@code user}/{@code model} and {@code Part}s of {@code text}, {@code functionCall}, {@code functionResponse},
 * {@code systemInstruction}, {@code tools[].functionDeclarations[]} with {@code parameters} or
 * {@code parametersJsonSchema}, {@code toolConfig.functionCallingConfig}, {@code generationConfig}), the
 * {@code GenerateContentResponse} ({@code candidates[].content.parts[]}, {@code Part.thought}, {@code FunctionCall}
 * with an object {@code args}, {@code FinishReason}, {@code UsageMetadata}, {@code modelVersion}, {@code responseId})
 * and {@code streamGenerateContent}, whose response is a stream of {@code GenerateContentResponse} instances, here
 * one per SSE {@code data} line ({@code alt=sse} as in the documented curl examples), the last one carrying the
 * {@code finishReason} and {@code usageMetadata}</li>
 * <li><a href="https://ai.google.dev/api/embeddings">https://ai.google.dev/api/embeddings</a>: {@code embedContent}
 * ({@code content} in,
 * {@code EmbedContentResponse.embedding.values} out) and {@code batchEmbedContents} ({@code requests[]} in,
 * {@code embeddings[].values} out, in request order)</li>
 * <li><a href="https://google.aip.dev/193">https://google.aip.dev/193</a> and <a href=
 * "https://github.com/googleapis/googleapis/blob/master/google/rpc/code.proto">https://github.com/googleapis/googleapis/blob/master/google/rpc/code.proto</a>:
 * the
 * error envelope {@code {"error":{"code","message","status"}}} with {@code code} the HTTP status and {@code status}
 * the {@code google.rpc.Code} name mapped from it</li>
 * </ul>
 * {@code ChatReply.thinking} becomes a leading part with {@code thought: true}. A function call carries an
 * {@code id} only when the reply's tool call has one, and ends the turn with {@code STOP} as in Gemini. Gemini puts the
 * model in the path, so the match variant carries the model id. In the dialect-neutral view {@code systemInstruction}
 * becomes a {@code system} message, role {@code model} becomes {@code assistant} and each {@code functionResponse}
 * part becomes a {@code tool} message whose text is the {@code response} object as JSON, as in the OpenAI view.
 */
public class GeminiDialect implements Dialect {

    private static final Pattern PATH = Pattern
            .compile("^/v1beta/models/([^/:]+):(generateContent|streamGenerateContent|embedContent|batchEmbedContents)$");

    private final String id;
    private final String pathPrefix;
    private final ObjectMapper mapper = MockApiJson.mapper();

    public GeminiDialect(String id, String pathPrefix) {
        this.id = id;
        this.pathPrefix = pathPrefix;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String pathPrefix() {
        return pathPrefix;
    }

    @Override
    public Optional<Match> match(String method, String path, Map<String, List<String>> query, JsonNode bodyOrNull) {
        Matcher matcher = PATH.matcher(path);
        if (!"POST".equals(method) || !matcher.matches()) {
            return Optional.empty();
        }
        String model = matcher.group(1);
        return switch (matcher.group(2)) {
            case "embedContent", "batchEmbedContents" -> Optional.of(new Match(Operation.EMBEDDINGS, false, model));
            default -> chatMatch(matcher.group(2), model, query);
        };
    }

    /**
     * The match for the {@code generateContent} and {@code streamGenerateContent} methods of {@code model}, the latter
     * only in its SSE flavour ({@code alt=sse}); empty for any other method.
     */
    protected static Optional<Match> chatMatch(String method, String model, Map<String, List<String>> query) {
        return switch (method) {
            case "generateContent" -> Optional.of(new Match(Operation.CHAT, false, model));
            case "streamGenerateContent" -> query.getOrDefault("alt", List.of()).contains("sse")
                    ? Optional.of(new Match(Operation.CHAT, true, model))
                    : Optional.empty();
            default -> Optional.empty();
        };
    }

    /**
     * Whether a function call carries the reply's tool call id, when it has one.
     */
    protected boolean functionCallIds() {
        return true;
    }

    @Override
    public ChatRequestView parseChat(Match m, JsonNode body) {
        List<ChatRequestView.Message> messages = new ArrayList<>();
        JsonNode system = body.get("systemInstruction");
        if (system != null && !system.isNull()) {
            messages.add(new MessageImpl("system", partsText(system), null, null, null));
        }
        for (JsonNode content : body.path("contents")) {
            parseContent(content, messages);
        }
        List<ChatRequestView.ToolDefinition> tools = new ArrayList<>();
        for (JsonNode tool : body.path("tools")) {
            for (JsonNode fn : tool.path("functionDeclarations")) {
                tools.add(new ToolDefinitionImpl(text(fn, "name"), text(fn, "description"),
                        firstNonNull(fn, "parameters", "parametersJsonSchema")));
            }
        }
        JsonNode config = body.path("generationConfig");
        return new ChatRequestViewImpl(
                m.variant(),
                m.streaming(),
                config.hasNonNull("temperature") ? config.get("temperature").asDouble() : null,
                config.hasNonNull("topP") ? config.get("topP").asDouble() : null,
                config.hasNonNull("maxOutputTokens") ? config.get("maxOutputTokens").asInt() : null,
                messages,
                tools,
                toolChoice(body.path("toolConfig").path("functionCallingConfig")),
                text(config, "responseMimeType"),
                firstNonNull(config, "responseSchema", "responseJsonSchema"));
    }

    private static void parseContent(JsonNode content, List<ChatRequestView.Message> out) {
        String role = "model".equals(text(content, "role")) ? "assistant" : "user";
        StringBuilder text = null;
        List<ToolCall> toolCalls = null;
        for (JsonNode part : content.path("parts")) {
            if (part.hasNonNull("functionCall")) {
                JsonNode fc = part.get("functionCall");
                toolCalls = toolCalls == null ? new ArrayList<>() : toolCalls;
                toolCalls.add(ToolCall.of(text(fc, "id"), text(fc, "name"), fc.path("args").toString()));
            } else if (part.hasNonNull("functionResponse")) {
                JsonNode fr = part.get("functionResponse");
                out.add(new MessageImpl("tool", fr.path("response").toString(), null, text(fr, "id"), text(fr, "name")));
            } else if (part.hasNonNull("text") && !part.path("thought").asBoolean(false)) {
                text = text == null ? new StringBuilder() : text;
                text.append(part.get("text").asText());
            }
        }
        if (text != null || toolCalls != null) {
            out.add(new MessageImpl(role, text == null ? null : text.toString(), toolCalls, null, null));
        }
    }

    /**
     * The concatenated {@code text} of the parts that have one.
     */
    private static String partsText(JsonNode content) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : content.path("parts")) {
            if (part.hasNonNull("text")) {
                sb.append(part.get("text").asText());
            }
        }
        return sb.toString();
    }

    /**
     * {@code ANY} restricted to a single function gives that function's name, the other modes their lower case name.
     */
    private static String toolChoice(JsonNode config) {
        String mode = text(config, "mode");
        if (mode == null) {
            return null;
        }
        JsonNode allowed = config.path("allowedFunctionNames");
        if ("ANY".equals(mode) && allowed.size() == 1) {
            return allowed.get(0).asText();
        }
        return mode.toLowerCase();
    }

    private static JsonNode firstNonNull(JsonNode node, String field, String alternative) {
        if (node.hasNonNull(field)) {
            return node.get(field);
        }
        return node.hasNonNull(alternative) ? node.get(alternative) : null;
    }

    @Override
    public Rendered render(Match m, Reply reply, CapturedRequest req) {
        if (reply instanceof ChatReply chat && m.operation() == Operation.CHAT) {
            return m.streaming() ? renderStream(m, chat, req) : renderResponse(m, chat, req);
        }
        if (reply instanceof EmbeddingReply embeddings && m.operation() == Operation.EMBEDDINGS) {
            return renderEmbeddings(embeddings, req);
        }
        throw UnrenderableReply.unsupported("Gemini", m, reply);
    }

    private Rendered renderResponse(Match m, ChatReply reply, CapturedRequest req) {
        List<ObjectNode> parts = new ArrayList<>();
        if (reply.thinking() != null) {
            parts.add(thought(reply.thinking()));
        }
        List<ToolCall> toolCalls = toolCalls(reply);
        if (reply.text() != null || toolCalls.isEmpty()) {
            parts.add(textPart(reply.text() == null ? "" : reply.text()));
        }
        toolCalls.forEach(tc -> parts.add(functionCall(tc)));
        ObjectNode root = response(m, reply, req, parts, true);
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    private Rendered renderStream(Match m, ChatReply reply, CapturedRequest req) {
        List<List<ObjectNode>> chunks = new ArrayList<>();
        if (reply.thinking() != null) {
            chunks.add(List.of(thought(reply.thinking())));
        }
        List<String> texts = Chunker.chunks(reply);
        List<ToolCall> toolCalls = toolCalls(reply);
        if (texts.isEmpty() && toolCalls.isEmpty()) {
            texts = List.of("");
        }
        texts.forEach(t -> chunks.add(List.of(textPart(t))));
        if (!toolCalls.isEmpty()) {
            chunks.add(toolCalls.stream().map(this::functionCall).toList());
        }
        List<SseEvent> events = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            ObjectNode chunk = response(m, reply, req, chunks.get(i), i == chunks.size() - 1);
            events.add(SseEvent.of(null, chunk.toString()));
        }
        return Rendered.stream(Rendered.StreamFormat.SSE, events);
    }

    /**
     * One {@code GenerateContentResponse} with a single candidate; {@code finishReason} and {@code usageMetadata} only
     * when {@code last}.
     */
    private ObjectNode response(Match m, ChatReply reply, CapturedRequest req, List<ObjectNode> parts, boolean last) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode candidate = root.putArray("candidates").addObject();
        ObjectNode content = candidate.putObject("content");
        content.putArray("parts").addAll(parts);
        content.put("role", "model");
        if (last) {
            candidate.put("finishReason", finishReason(reply.finishReason()));
        }
        candidate.put("index", 0);
        if (last) {
            usage(root, reply.usage());
        }
        root.put("modelVersion", reply.modelName() != null ? reply.modelName() : m.variant());
        root.put("responseId", reply.id() != null ? reply.id() : "mock-response-" + req.index());
        return root;
    }

    private static void usage(ObjectNode root, Usage usage) {
        int in = usage == null ? 0 : usage.inputTokens();
        int out = usage == null ? 0 : usage.outputTokens();
        ObjectNode u = root.putObject("usageMetadata");
        u.put("promptTokenCount", in);
        if (usage != null && usage.cacheReadTokens() != null) {
            u.put("cachedContentTokenCount", usage.cacheReadTokens());
        }
        u.put("candidatesTokenCount", out);
        u.put("totalTokenCount", in + out);
    }

    private ObjectNode textPart(String text) {
        return mapper.createObjectNode().put("text", text);
    }

    private ObjectNode thought(String thinking) {
        return mapper.createObjectNode().put("text", thinking).put("thought", true);
    }

    private ObjectNode functionCall(ToolCall tc) {
        ObjectNode fc = mapper.createObjectNode();
        if (functionCallIds() && tc.id() != null) {
            fc.put("id", tc.id());
        }
        fc.put("name", tc.name());
        fc.set("args", Json.argumentsObject(tc));
        ObjectNode part = mapper.createObjectNode();
        part.set("functionCall", fc);
        return part;
    }

    private static List<ToolCall> toolCalls(ChatReply reply) {
        return reply.toolCalls() == null ? List.of() : reply.toolCalls();
    }

    private static String finishReason(FinishReason reason) {
        return switch (reason) {
            case STOP, TOOL_CALLS -> "STOP";
            case LENGTH -> "MAX_TOKENS";
            case CONTENT_FILTER -> "SAFETY";
        };
    }

    private Rendered renderEmbeddings(EmbeddingReply reply, CapturedRequest req) {
        JsonNode body = req.bodyAsJson();
        boolean batch = body.has("requests");
        List<String> inputs = new ArrayList<>();
        if (batch) {
            body.get("requests").forEach(r -> inputs.add(partsText(r.path("content"))));
        } else {
            inputs.add(partsText(body.path("content")));
        }

        List<float[]> vectors = DerivedEmbeddings.vectors(reply, inputs);

        ObjectNode root = mapper.createObjectNode();
        if (batch) {
            ArrayNode embeddings = root.putArray("embeddings");
            vectors.forEach(v -> DerivedEmbeddings.floats(embeddings.addObject().putArray("values"), v));
        } else {
            DerivedEmbeddings.floats(root.putObject("embedding").putArray("values"), vectors.get(0));
        }
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    @Override
    public Rendered renderError(Match m, int status, String message) {
        return googleError(status, message);
    }

    /**
     * The error envelope that Google APIs share (AIP-193), also used by the Vertex AI {@code predict} dialect.
     */
    public static Rendered googleError(int status, String message) {
        ObjectNode root = MockApiJson.mapper().createObjectNode();
        root.putObject("error").put("code", status).put("message", message).put("status", errorStatus(status));
        return Rendered.json(status, MockApiJson.toBytes(root));
    }

    /**
     * The {@code google.rpc.Code} whose documented HTTP mapping is {@code status}.
     */
    private static String errorStatus(int status) {
        return switch (status) {
            case 400 -> "INVALID_ARGUMENT";
            case 401 -> "UNAUTHENTICATED";
            case 403 -> "PERMISSION_DENIED";
            case 404 -> "NOT_FOUND";
            case 409 -> "ABORTED";
            case 429 -> "RESOURCE_EXHAUSTED";
            case 499 -> "CANCELLED";
            case 501 -> "UNIMPLEMENTED";
            case 503 -> "UNAVAILABLE";
            case 504 -> "DEADLINE_EXCEEDED";
            default -> status >= 500 ? "INTERNAL" : "FAILED_PRECONDITION";
        };
    }
}
