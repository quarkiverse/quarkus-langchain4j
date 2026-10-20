package io.quarkiverse.langchain4j.mockapi.internal.dialect.openai;

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
import io.quarkiverse.langchain4j.mockapi.DecisionReply;
import io.quarkiverse.langchain4j.mockapi.DecisionRequestView;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.FinishReason;
import io.quarkiverse.langchain4j.mockapi.ModerationReply;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.Reply;
import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Chunker;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.DerivedEmbeddings;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Json;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.ChatRequestViewImpl;

/**
 * OpenAI wire format. Field names follow the published OpenAI OpenAPI spec (openai/openai-openapi):
 * <ul>
 * <li>requests: {@code CreateChatCompletionRequest}, {@code ChatCompletionRequestMessage},
 * {@code ChatCompletionTool}, {@code FunctionObject}, {@code ChatCompletionToolChoiceOption},
 * {@code ChatCompletionNamedToolChoice}, {@code ResponseFormatJsonSchema}</li>
 * <li>responses: {@code CreateChatCompletionResponse}, {@code ChatCompletionResponseMessage},
 * {@code ChatCompletionMessageToolCall}, {@code CompletionUsage}</li>
 * <li>streaming: {@code CreateChatCompletionStreamResponse}, {@code ChatCompletionStreamResponseDelta},
 * {@code ChatCompletionMessageToolCallChunk}, {@code ChatCompletionStreamOptions}</li>
 * <li>embeddings: {@code CreateEmbeddingRequest}, {@code CreateEmbeddingResponse}, {@code Embedding}</li>
 * <li>moderation: {@code CreateModerationResponse}</li>
 * <li>Responses API (see {@link OpenAiResponsesSupport}): {@code CreateResponse}, {@code Response}, {@code OutputItem},
 * {@code OutputMessage}, {@code OutputTextContent}, {@code FunctionToolCall}, {@code ReasoningItem},
 * {@code SummaryTextContent}, {@code ResponseUsage}, {@code ResponseStreamEvent} and its concrete events
 * ({@code ResponseCreatedEvent}, {@code ResponseInProgressEvent}, {@code ResponseOutputItemAddedEvent},
 * {@code ResponseContentPartAddedEvent}, {@code ResponseTextDeltaEvent}, {@code ResponseTextDoneEvent},
 * {@code ResponseContentPartDoneEvent}, {@code ResponseOutputItemDoneEvent},
 * {@code ResponseFunctionCallArgumentsDeltaEvent}, {@code ResponseFunctionCallArgumentsDoneEvent},
 * {@code ResponseCompletedEvent})</li>
 * <li>Decisions API (see {@link OpenAiDecisionsSupport}): {@code DecisionRequest}, {@code DecisionInput},
 * {@code DecisionInputMessage}, {@code QuestionParam} ({@code QuestionParamPredicate}, {@code QuestionParamChoice},
 * {@code QuestionParamScore}, {@code ChoiceOptionParam}, {@code ScoreLevelParam}), {@code DecisionResponse},
 * {@code AnswerResource} ({@code AnswerResourcePredicate}, {@code AnswerResourceChoice}, {@code AnswerResourceScore},
 * {@code AnswerResourceRefusal}), {@code ChoiceProbabilityResource}, {@code ScoreProbabilityResource},
 * {@code ResponseUsageResource}</li>
 * <li>errors: {@code ErrorResponse}, {@code Error}</li>
 * </ul>
 * Image generation and transcription are matched but not rendered: their replies are answered with a 500 error.
 * {@code ChatReply.thinking} is not rendered for chat completions (no documented field) but becomes a {@code reasoning}
 * output item with a {@code summary_text} summary in the Responses API.
 */
public class OpenAiDialect implements Dialect {

    private static final String CHAT_COMPLETIONS = "chat-completions";

    private static final List<String> MODERATION_CATEGORIES = List.of("hate", "hate/threatening", "harassment",
            "harassment/threatening", "illicit", "illicit/violent", "self-harm", "self-harm/intent",
            "self-harm/instructions", "sexual", "sexual/minors", "violence", "violence/graphic");

    private final ObjectMapper mapper = MockApiJson.mapper();
    private final OpenAiResponsesSupport responses = new OpenAiResponsesSupport();
    private final OpenAiDecisionsSupport decisions = new OpenAiDecisionsSupport();

    @Override
    public String id() {
        return "openai";
    }

    @Override
    public Optional<Match> match(String method, String path, Map<String, List<String>> query, JsonNode bodyOrNull) {
        if (!"POST".equals(method)) {
            return Optional.empty();
        }
        if (path.endsWith("/chat/completions")) {
            boolean streaming = bodyOrNull != null && bodyOrNull.path("stream").asBoolean(false);
            return Optional.of(new Match(Operation.CHAT, streaming, CHAT_COMPLETIONS));
        }
        if (path.endsWith("/embeddings")) {
            return Optional.of(new Match(Operation.EMBEDDINGS, false, "embeddings"));
        }
        if (path.endsWith("/moderations")) {
            return Optional.of(new Match(Operation.MODERATION, false, "moderations"));
        }
        if (path.endsWith("/responses")) {
            boolean streaming = bodyOrNull != null && bodyOrNull.path("stream").asBoolean(false);
            return Optional.of(new Match(Operation.CHAT, streaming, "responses"));
        }
        if (path.endsWith("/decisions")) {
            return Optional.of(new Match(Operation.DECISION, false, "decisions"));
        }
        if (path.endsWith("/images/generations")) {
            return Optional.of(new Match(Operation.IMAGE, false, "images"));
        }
        if (path.endsWith("/audio/transcriptions")) {
            return Optional.of(new Match(Operation.TRANSCRIPTION, false, "transcriptions"));
        }
        return Optional.empty();
    }

    @Override
    public ChatRequestView parseChat(Match m, JsonNode body) {
        if ("responses".equals(m.variant())) {
            return responses.parse(body);
        }
        List<ChatRequestView.Message> messages = new ArrayList<>();
        for (JsonNode msg : body.path("messages")) {
            messages.add(Json.chatCompletionsMessage(msg));
        }
        return new ChatRequestViewImpl(
                text(body, "model"),
                body.path("stream").asBoolean(false),
                body.hasNonNull("temperature") ? body.get("temperature").asDouble() : null,
                body.hasNonNull("top_p") ? body.get("top_p").asDouble() : null,
                maxTokens(body),
                messages,
                Json.functionTools(body.path("tools")),
                toolChoice(body.get("tool_choice")),
                text(body.path("response_format"), "type"),
                Json.responseSchema(body));
    }

    @Override
    public DecisionRequestView parseDecision(Match m, JsonNode body) {
        return decisions.parse(body);
    }

    private static Integer maxTokens(JsonNode body) {
        if (body.hasNonNull("max_tokens")) {
            return body.get("max_tokens").asInt();
        }
        if (body.hasNonNull("max_completion_tokens")) {
            return body.get("max_completion_tokens").asInt();
        }
        return null;
    }

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
            if ("responses".equals(m.variant())) {
                return m.streaming() ? responses.renderStream(chat, req) : responses.render(chat, req);
            }
            if (CHAT_COMPLETIONS.equals(m.variant())) {
                return m.streaming() ? renderChatStream(chat, req) : renderChat(chat, req);
            }
        }
        if (reply instanceof EmbeddingReply embeddings && m.operation() == Operation.EMBEDDINGS) {
            return renderEmbeddings(embeddings, req);
        }
        if (reply instanceof ModerationReply moderation && m.operation() == Operation.MODERATION) {
            return renderModeration(moderation, req);
        }
        if (reply instanceof DecisionReply decision && m.operation() == Operation.DECISION) {
            return decisions.render(decision, req);
        }
        throw new UnrenderableReply("OpenAI render not supported: " + m.operation() + "/" + m.variant() + " with "
                + reply.getClass().getSimpleName());
    }

    private Rendered renderChat(ChatReply reply, CapturedRequest req) {
        ObjectNode root = mapper.createObjectNode();
        root.put("id", reply.id() != null ? reply.id() : "chatcmpl-mock-" + req.index());
        root.put("object", "chat.completion");
        root.put("created", Instant.now().getEpochSecond());
        root.put("model", Json.modelName(reply, req));

        ObjectNode message = mapper.createObjectNode();
        message.put("role", "assistant");
        if (reply.text() != null) {
            message.put("content", reply.text());
        } else {
            message.putNull("content");
        }
        if (reply.toolCalls() != null && !reply.toolCalls().isEmpty()) {
            ArrayNode calls = message.putArray("tool_calls");
            for (int i = 0; i < reply.toolCalls().size(); i++) {
                Json.functionCall(calls, reply.toolCalls().get(i), i, false);
            }
        }

        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("index", 0);
        choice.set("message", message);
        choice.putNull("logprobs");
        choice.put("finish_reason", finishReason(reply.finishReason()));

        Json.chatUsage(root, reply.usage());
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    private Rendered renderChatStream(ChatReply reply, CapturedRequest req) {
        String id = reply.id() != null ? reply.id() : "chatcmpl-mock-" + req.index();
        long created = Instant.now().getEpochSecond();
        String model = Json.modelName(reply, req);
        boolean includeUsage = req.bodyAsJson().path("stream_options").path("include_usage").asBoolean(false);
        List<SseEvent> events = new ArrayList<>();
        boolean first = true;

        for (String piece : Chunker.chunks(reply)) {
            ObjectNode delta = mapper.createObjectNode();
            if (first) {
                delta.put("role", "assistant");
            }
            delta.put("content", piece);
            events.add(chunkEvent(id, created, model, includeUsage, delta, null));
            first = false;
        }
        if (reply.toolCalls() != null) {
            for (int i = 0; i < reply.toolCalls().size(); i++) {
                ObjectNode delta = mapper.createObjectNode();
                if (first) {
                    delta.put("role", "assistant");
                }
                Json.functionCall(delta.putArray("tool_calls"), reply.toolCalls().get(i), i, true);
                events.add(chunkEvent(id, created, model, includeUsage, delta, null));
                first = false;
            }
        }
        if (first) {
            ObjectNode delta = mapper.createObjectNode();
            delta.put("role", "assistant");
            delta.put("content", "");
            events.add(chunkEvent(id, created, model, includeUsage, delta, null));
        }
        events.add(chunkEvent(id, created, model, includeUsage, mapper.createObjectNode(),
                finishReason(reply.finishReason())));

        if (includeUsage) {
            ObjectNode root = chunkRoot(id, created, model);
            root.putArray("choices");
            Json.chatUsage(root, reply.usage());
            events.add(SseEvent.of(null, root.toString()));
        }
        events.add(SseEvent.of(null, "[DONE]"));
        return Rendered.stream(Rendered.StreamFormat.SSE, events);
    }

    private ObjectNode chunkRoot(String id, long created, String model) {
        ObjectNode root = mapper.createObjectNode();
        root.put("id", id);
        root.put("object", "chat.completion.chunk");
        root.put("created", created);
        root.put("model", model);
        return root;
    }

    private SseEvent chunkEvent(String id, long created, String model, boolean includeUsage, ObjectNode delta,
            String finishReason) {
        ObjectNode root = chunkRoot(id, created, model);
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("index", 0);
        choice.set("delta", delta);
        choice.putNull("logprobs");
        if (finishReason == null) {
            choice.putNull("finish_reason");
        } else {
            choice.put("finish_reason", finishReason);
        }
        if (includeUsage) {
            root.putNull("usage");
        }
        return SseEvent.of(null, root.toString());
    }

    private Rendered renderEmbeddings(EmbeddingReply reply, CapturedRequest req) {
        List<String> inputs = Json.strings(req.bodyAsJson().get("input"));
        List<float[]> vectors = DerivedEmbeddings.vectors(reply, inputs);

        ObjectNode root = mapper.createObjectNode();
        root.put("object", "list");
        ArrayNode data = root.putArray("data");
        for (int i = 0; i < vectors.size(); i++) {
            ObjectNode e = data.addObject();
            e.put("object", "embedding");
            e.put("index", i);
            DerivedEmbeddings.floats(e.putArray("embedding"), vectors.get(i));
        }
        root.put("model", Json.requestModel(req, "mock-embedding-model"));
        ObjectNode usage = root.putObject("usage");
        usage.put("prompt_tokens", inputs.size());
        usage.put("total_tokens", inputs.size());
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    private Rendered renderModeration(ModerationReply reply, CapturedRequest req) {
        List<String> flagged = reply.categories() == null ? List.of() : reply.categories();
        ObjectNode root = mapper.createObjectNode();
        root.put("id", "modr-mock-" + req.index());
        root.put("model", Json.requestModel(req, "mock-moderation-model"));
        ObjectNode result = root.putArray("results").addObject();
        result.put("flagged", reply.flagged());
        ObjectNode categories = result.putObject("categories");
        ObjectNode scores = result.putObject("category_scores");
        ObjectNode inputTypes = result.putObject("category_applied_input_types");
        List<String> names = new ArrayList<>(MODERATION_CATEGORIES);
        flagged.stream().filter(c -> !names.contains(c)).forEach(names::add);
        for (String name : names) {
            boolean on = flagged.contains(name);
            categories.put(name, on);
            scores.put(name, on ? 1.0 : 0.0);
            inputTypes.putArray(name).add("text");
        }
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    private static String finishReason(FinishReason reason) {
        return switch (reason) {
            case STOP -> "stop";
            case TOOL_CALLS -> "tool_calls";
            case LENGTH -> "length";
            case CONTENT_FILTER -> "content_filter";
        };
    }

    @Override
    public Rendered renderError(Match m, int status, String message) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode error = root.putObject("error");
        error.put("message", message);
        error.put("type", "mock_error");
        error.putNull("param");
        error.putNull("code");
        return Rendered.json(status, MockApiJson.toBytes(root));
    }
}
