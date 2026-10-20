package io.quarkiverse.langchain4j.mockapi.internal.dialect.watsonx;

import static io.quarkiverse.langchain4j.mockapi.internal.dialect.Json.text;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
import io.quarkiverse.langchain4j.mockapi.ModerationReply;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.Reply;
import io.quarkiverse.langchain4j.mockapi.ScoringReply;
import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkiverse.langchain4j.mockapi.ToolCall;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Chunker;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.DerivedEmbeddings;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Json;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.openai.OpenAiDialect;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.ChatRequestViewImpl;

/**
 * IBM watsonx.ai wire format, following the published OpenAPI spec of the watsonx.ai API
 * (<a href="https://cloud.ibm.com/apidocs/watsonx-ai.json">https://cloud.ibm.com/apidocs/watsonx-ai.json</a>, rendered at
 * <a href="https://cloud.ibm.com/apidocs/watsonx-ai">https://cloud.ibm.com/apidocs/watsonx-ai</a>) and the IBM
 * Cloud IAM Identity Services spec (<a href=
 * "https://cloud.ibm.com/apidocs/iam-identity-token-api.json">https://cloud.ibm.com/apidocs/iam-identity-token-api.json</a>):
 * <ul>
 * <li>chat ({@code POST /ml/v1/text/chat}, "Infer text"): request {@code TextChatRequest} ({@code model_id} or its
 * alias {@code model}, {@code TextChatMessages} with string or {@code TextChatUserContents} content, of which the
 * {@code text} parts are read, {@code TextChatParameterTools}, {@code tool_choice_option} or {@code tool_choice},
 * {@code TextChatParameters} with {@code max_completion_tokens}, else the deprecated {@code max_tokens},
 * {@code TextChatResponseFormat}); response {@code TextChatResponse} ({@code id}, {@code model_id}, {@code model},
 * {@code created}, {@code created_at}, {@code TextChatResultChoice}, {@code TextChatResultMessage},
 * {@code TextChatToolCall}, {@code TextChatUsage})</li>
 * <li>streaming chat ({@code POST /ml/v1/text/chat_stream}, "Infer text event stream"): {@code text/event-stream} of
 * {@code TextChatStreamItem}s, each with at least one {@code TextChatResultChoiceStream} whose
 * {@code TextChatResultDelta} carries the required {@code role}; tool calls are {@code TextChatToolCallStream}s with
 * their {@code index}. The events are {@code event: message} with the item as {@code data} (the framing the
 * watsonx.ai Node SDK documents for its event streams, which also number each event with an {@code id}: SSE ids are
 * not rendered). The spec itself documents {@code data:}-only events, of which {@code event: message} is the
 * equivalent explicit form. The spec documents no terminator: the stream ends after the item with the finish reason
 * and the usage.</li>
 * <li>deployment chat ({@code POST /ml/v1/deployments/{id_or_name}/text/chat} and {@code .../text/chat_stream}):
 * the same formats without a model; the deployment id is carried in {@link Match#variant()} and is the response's
 * {@code model_id} unless the reply names a model</li>
 * <li>embeddings ({@code POST /ml/v1/text/embeddings}): {@code EmbeddingsRequest} ({@code inputs}),
 * {@code EmbeddingsResponse} ({@code model_id}, {@code results[].embedding}, {@code created_at},
 * {@code input_token_count}, here the number of inputs)</li>
 * <li>rerank ({@code POST /ml/v1/text/rerank}): {@code RerankRequest} ({@code inputs[].text},
 * {@code parameters.return_options.top_n}), {@code RerankResponse} with {@code RerankedResults} ({@code index},
 * {@code score}) in input order, by choice: the spec's example is in neither input nor score order, and the client
 * maps the results by {@code index}; with {@code top_n} only the best {@code top_n} inputs are listed, still in input
 * order</li>
 * <li>detection ({@code POST /ml/v1/text/detection}, the moderation model): {@code TextDetectionContentRequest}
 * ({@code input}), {@code TextDetectionContentResponse} with one {@code TextDetectionContentItem} per flagged category
 * ({@code detection_type} the category, {@code detection} the whole input, {@code score} 1, {@code start}/{@code end}
 * spanning the input); not flagged gives no detections</li>
 * <li>model gateway ({@code POST /ml/gateway/v1/chat/completions} and {@code /ml/gateway/v1/embeddings}): OpenAI
 * compatible ({@code CreateChatsRequest}, {@code ChatCompletion}, {@code Embeddings}), rendered by
 * {@link OpenAiDialect} with the documented {@code 201} status; streaming is data-only server-sent events terminated
 * by {@code data: [DONE]} (the {@code stream} description); errors are {@code MGErrorResponse} ({@code error} with
 * {@code code} and {@code message})</li>
 * <li>errors: {@code ApiErrorResponse} ({@code trace}, {@code status_code}, {@code errors[]} of {@code ApiError} with
 * {@code code} and {@code message})</li>
 * <li>IAM token ({@code POST /identity/token}, {@code getToken_ApiKey}, form-encoded {@code grant_type} and
 * {@code apikey}): always answered, without a configured reply, with the fixed {@code access_token}
 * {@value #ACCESS_TOKEN} that expires in ten years ({@code token-response}); a request without {@code grant_type} or
 * {@code apikey} gets a 400 {@code OidcExceptionResponse} ({@code errorCode}, {@code errorMessage}). The provider
 * caches IAM tokens per api-key, so one token request serves every model.</li>
 * </ul>
 * The documented finish reasons are {@code stop}, {@code length}, {@code tool_calls}, {@code time_limit},
 * {@code cancelled} and {@code error}; {@code CONTENT_FILTER} has no counterpart and becomes {@code error}.
 * {@code ChatReply.thinking} is not rendered (the spec's result message has no reasoning field).
 * <p>
 * Everything else the watsonx.ai client calls is not modelled and is left to raw stubs: text generation and its
 * stream (also for deployments), tokenization, text extraction and classification (with their Cloud Object Storage
 * files), schema services, time series forecasts, built-in tools, batches, files, foundation model and gateway model
 * catalogs, deployment lookups, context and generated text detection, and the Cloud Pak for Data authentication
 * endpoints.
 */
public class WatsonxDialect implements Dialect {

    static final String ACCESS_TOKEN = "mock-iam-token";

    private static final String TEXT_CHAT = "text-chat";
    private static final String DEPLOYMENT = "deployment/";
    private static final String GATEWAY = "gateway";
    private static final Pattern DEPLOYMENT_CHAT = Pattern.compile("/ml/v1/deployments/([^/]+)/text/(chat|chat_stream)");
    private static final long TOKEN_LIFETIME_SECONDS = 10L * 365 * 24 * 3600;

    private final ObjectMapper mapper = MockApiJson.mapper();
    private final OpenAiDialect openAi = new OpenAiDialect();

    @Override
    public String id() {
        return "watsonx";
    }

    @Override
    public String pathPrefix() {
        return "/watsonx";
    }

    @Override
    public Optional<Match> match(String method, String path, Map<String, List<String>> query, JsonNode bodyOrNull) {
        if (!"POST".equals(method)) {
            return Optional.empty();
        }
        Matcher deployment = DEPLOYMENT_CHAT.matcher(path);
        if (deployment.matches()) {
            return Optional.of(new Match(Operation.CHAT, "chat_stream".equals(deployment.group(2)),
                    DEPLOYMENT + deployment.group(1)));
        }
        return switch (path) {
            case "/ml/v1/text/chat" -> Optional.of(new Match(Operation.CHAT, false, TEXT_CHAT));
            case "/ml/v1/text/chat_stream" -> Optional.of(new Match(Operation.CHAT, true, TEXT_CHAT));
            case "/ml/v1/text/embeddings" -> Optional.of(new Match(Operation.EMBEDDINGS, false, "embeddings"));
            case "/ml/v1/text/rerank" -> Optional.of(new Match(Operation.SCORING, false, "rerank"));
            case "/ml/v1/text/detection" -> Optional.of(new Match(Operation.MODERATION, false, "detection"));
            case "/ml/gateway/v1/chat/completions" -> Optional.of(new Match(Operation.CHAT,
                    bodyOrNull != null && bodyOrNull.path("stream").asBoolean(false), GATEWAY));
            case "/ml/gateway/v1/embeddings" -> Optional.of(new Match(Operation.EMBEDDINGS, false, GATEWAY));
            default -> Optional.empty();
        };
    }

    @Override
    public Optional<Rendered> fixedResponse(String method, String path, String bodyOrNull) {
        if (!"POST".equals(method) || !"/identity/token".equals(path)) {
            return Optional.empty();
        }
        Map<String, String> form = form(bodyOrNull);
        for (String required : List.of("grant_type", "apikey")) {
            if (form.get(required) == null || form.get(required).isEmpty()) {
                ObjectNode error = mapper.createObjectNode();
                error.put("errorCode", "mock_error");
                error.put("errorMessage", "Parameter " + required + " is missing");
                return Optional.of(Rendered.json(400, MockApiJson.toBytes(error)));
            }
        }
        long now = Instant.now().getEpochSecond();
        ObjectNode token = mapper.createObjectNode();
        token.put("access_token", ACCESS_TOKEN);
        token.put("refresh_token", "not_supported");
        token.put("token_type", "bearer");
        token.put("expires_in", TOKEN_LIFETIME_SECONDS);
        token.put("expiration", now + TOKEN_LIFETIME_SECONDS);
        return Optional.of(Rendered.json(200, MockApiJson.toBytes(token)));
    }

    private static Map<String, String> form(String body) {
        Map<String, String> form = new HashMap<>();
        if (body == null || body.isEmpty()) {
            return form;
        }
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                form.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return form;
    }

    @Override
    public ChatRequestView parseChat(Match m, JsonNode body) {
        if (GATEWAY.equals(m.variant())) {
            return openAi.parseChat(openAiMatch(m), body);
        }
        List<ChatRequestView.Message> messages = new ArrayList<>();
        for (JsonNode msg : body.path("messages")) {
            messages.add(Json.chatCompletionsMessage(msg));
        }
        return new ChatRequestViewImpl(
                body.hasNonNull("model_id") ? text(body, "model_id") : text(body, "model"),
                m.streaming(),
                body.hasNonNull("temperature") ? body.get("temperature").asDouble() : null,
                body.hasNonNull("top_p") ? body.get("top_p").asDouble() : null,
                maxTokens(body),
                messages,
                Json.functionTools(body.path("tools")),
                toolChoice(body),
                text(body.path("response_format"), "type"),
                Json.responseSchema(body));
    }

    private static Integer maxTokens(JsonNode body) {
        if (body.hasNonNull("max_completion_tokens")) {
            return body.get("max_completion_tokens").asInt();
        }
        return body.hasNonNull("max_tokens") ? body.get("max_tokens").asInt() : null;
    }

    /**
     * {@code tool_choice_option} as is, a {@code tool_choice} as its function's name.
     */
    private static String toolChoice(JsonNode body) {
        if (body.hasNonNull("tool_choice_option")) {
            return body.get("tool_choice_option").asText();
        }
        return text(body.path("tool_choice").path("function"), "name");
    }

    @Override
    public Rendered render(Match m, Reply reply, CapturedRequest req) {
        if (GATEWAY.equals(m.variant())) {
            Rendered rendered = openAi.render(openAiMatch(m), reply, req);
            return m.streaming() ? rendered
                    : new Rendered(201, rendered.headers(), rendered.body(), rendered.events(), rendered.streamFormat());
        }
        if (reply instanceof ChatReply chat && m.operation() == Operation.CHAT) {
            return m.streaming() ? renderChatStream(m, chat, req) : renderChat(m, chat, req);
        }
        if (reply instanceof EmbeddingReply embeddings && m.operation() == Operation.EMBEDDINGS) {
            return renderEmbeddings(embeddings, req);
        }
        if (reply instanceof ScoringReply scoring && m.operation() == Operation.SCORING) {
            return renderRerank(scoring, req);
        }
        if (reply instanceof ModerationReply moderation && m.operation() == Operation.MODERATION) {
            return renderDetection(moderation, req);
        }
        throw UnrenderableReply.unsupported("watsonx", m, reply);
    }

    /**
     * The OpenAI match of a model gateway request.
     */
    private static Match openAiMatch(Match m) {
        return new Match(m.operation(), m.streaming(),
                m.operation() == Operation.CHAT ? "chat-completions" : "embeddings");
    }

    private Rendered renderChat(Match m, ChatReply reply, CapturedRequest req) {
        ObjectNode root = chatRoot(m, reply, req);
        ObjectNode message = mapper.createObjectNode();
        message.put("role", "assistant");
        if (reply.text() != null) {
            message.put("content", reply.text());
        }
        List<ToolCall> toolCalls = reply.toolCalls() == null ? List.of() : reply.toolCalls();
        if (!toolCalls.isEmpty()) {
            ArrayNode calls = message.putArray("tool_calls");
            for (int i = 0; i < toolCalls.size(); i++) {
                Json.functionCall(calls, toolCalls.get(i), i, false);
            }
        }
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("index", 0);
        choice.set("message", message);
        choice.put("finish_reason", finishReason(reply.finishReason()));
        Json.chatUsage(root, reply.usage());
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    private Rendered renderChatStream(Match m, ChatReply reply, CapturedRequest req) {
        List<SseEvent> events = new ArrayList<>();
        for (String piece : Chunker.chunks(reply)) {
            events.add(chunk(m, reply, req, delta().put("content", piece), false));
        }
        List<ToolCall> toolCalls = reply.toolCalls() == null ? List.of() : reply.toolCalls();
        for (int i = 0; i < toolCalls.size(); i++) {
            ObjectNode delta = delta();
            Json.functionCall(delta.putArray("tool_calls"), toolCalls.get(i), i, true);
            events.add(chunk(m, reply, req, delta, false));
        }
        events.add(chunk(m, reply, req, delta(), true));
        return Rendered.stream(Rendered.StreamFormat.SSE, events);
    }

    /**
     * A {@code TextChatResultDelta}, whose {@code role} is required.
     */
    private ObjectNode delta() {
        return mapper.createObjectNode().put("role", "assistant");
    }

    /**
     * A {@code TextChatStreamItem}; the last one carries the finish reason and the usage.
     */
    private SseEvent chunk(Match m, ChatReply reply, CapturedRequest req, ObjectNode delta, boolean last) {
        ObjectNode root = chatRoot(m, reply, req);
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("index", 0);
        choice.set("delta", delta);
        if (last) {
            choice.put("finish_reason", finishReason(reply.finishReason()));
            Json.chatUsage(root, reply.usage());
        } else {
            choice.putNull("finish_reason");
        }
        return SseEvent.of("message", root.toString());
    }

    private ObjectNode chatRoot(Match m, ChatReply reply, CapturedRequest req) {
        String model = reply.modelName() != null ? reply.modelName() : requestModel(m, req);
        Instant now = Instant.now();
        ObjectNode root = mapper.createObjectNode();
        root.put("id", reply.id() != null ? reply.id() : "chat-mock-" + req.index());
        root.put("model_id", model);
        root.put("model", model);
        root.put("created", now.getEpochSecond());
        root.put("created_at", now.toString());
        return root;
    }

    private static String requestModel(Match m, CapturedRequest req) {
        if (m.variant().startsWith(DEPLOYMENT)) {
            return m.variant().substring(DEPLOYMENT.length());
        }
        return Json.requestText(req, "model_id", Json.requestModel(req, "mock-model"));
    }

    private static String finishReason(FinishReason reason) {
        return switch (reason) {
            case STOP -> "stop";
            case TOOL_CALLS -> "tool_calls";
            case LENGTH -> "length";
            case CONTENT_FILTER -> "error";
        };
    }

    private Rendered renderEmbeddings(EmbeddingReply reply, CapturedRequest req) {
        JsonNode body = req.bodyAsJson();
        List<String> inputs = new ArrayList<>();
        body.path("inputs").forEach(i -> inputs.add(i.asText()));
        List<float[]> vectors = DerivedEmbeddings.vectors(reply, inputs);

        ObjectNode root = mapper.createObjectNode();
        root.put("model_id", body.hasNonNull("model_id") ? body.get("model_id").asText() : "mock-embedding-model");
        ArrayNode results = root.putArray("results");
        for (float[] vector : vectors) {
            DerivedEmbeddings.floats(results.addObject().putArray("embedding"), vector);
        }
        root.put("created_at", Instant.now().toString());
        root.put("input_token_count", inputs.size());
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    private Rendered renderRerank(ScoringReply reply, CapturedRequest req) {
        JsonNode body = req.bodyAsJson();
        int inputs = body.path("inputs").size();
        List<Double> scores = DerivedEmbeddings.scores(reply, inputs, "inputs");

        List<Integer> listed = new ArrayList<>();
        for (int i = 0; i < inputs; i++) {
            listed.add(i);
        }
        JsonNode topN = body.path("parameters").path("return_options").path("top_n");
        if (topN.isNumber() && topN.asInt() < inputs) {
            // stable, so equal scores keep input order
            listed.sort(Comparator.comparing((Integer i) -> scores.get(i)).reversed());
            listed = new ArrayList<>(listed.subList(0, Math.max(0, topN.asInt())));
            listed.sort(Comparator.naturalOrder());
        }

        ObjectNode root = mapper.createObjectNode();
        root.put("model_id", body.hasNonNull("model_id") ? body.get("model_id").asText() : "mock-rerank-model");
        ArrayNode results = root.putArray("results");
        for (int index : listed) {
            results.addObject().put("index", index).put("score", scores.get(index));
        }
        root.put("created_at", Instant.now().toString());
        root.put("input_token_count", inputs);
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    private Rendered renderDetection(ModerationReply reply, CapturedRequest req) {
        String input = req.bodyAsJson().path("input").asText("");
        ObjectNode root = mapper.createObjectNode();
        ArrayNode detections = root.putArray("detections");
        if (reply.flagged() && reply.categories() != null) {
            for (String category : reply.categories()) {
                ObjectNode detection = detections.addObject();
                detection.put("start", 0);
                detection.put("end", input.length());
                detection.put("detection_type", category);
                // DIVERGENCE: the client reads the flagged text from an undocumented "text" field, so its Moderation
                // has no flagged text; the documented item carries the detected string in "detection"
                detection.put("detection", input);
                detection.put("score", 1.0);
            }
        }
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    @Override
    public Rendered renderError(Match m, int status, String message) {
        ObjectNode root = mapper.createObjectNode();
        if (GATEWAY.equals(m.variant())) {
            ObjectNode error = root.putObject("error");
            error.put("code", String.valueOf(status));
            error.put("message", message);
            error.put("type", "mock_error");
            return Rendered.json(status, MockApiJson.toBytes(root));
        }
        root.put("trace", UUID.randomUUID().toString());
        root.put("status_code", status);
        root.putArray("errors").addObject().put("code", errorCode(status)).put("message", message);
        return Rendered.json(status, MockApiJson.toBytes(root));
    }

    /**
     * {@code ApiError.code} is a free-form string; where one fits, a code the watsonx.ai client maps to a specific
     * exception, never {@code authentication_token_expired}, which would make the client refresh its token and retry.
     */
    private static String errorCode(int status) {
        return switch (status) {
            case 400 -> "invalid_input_argument";
            case 401 -> "authorization_rejected";
            case 403 -> "user_authorization_failed";
            case 404 -> "model_not_supported";
            case 429 -> "token_quota_reached";
            default -> "mock_error";
        };
    }
}
