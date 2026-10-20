package io.quarkiverse.langchain4j.mockapi.internal.dialect.bedrock;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
 * Amazon Bedrock Runtime wire format (REST-JSON), following the Bedrock Runtime API reference
 * (<a href=
 * "https://docs.aws.amazon.com/bedrock/latest/APIReference/">https://docs.aws.amazon.com/bedrock/latest/APIReference/</a>) and
 * its machine-readable service model (the AWS SDK for
 * Java v2's {@code services/bedrockruntime/.../service-2.json}). The model id is a URI label, percent-decoded and
 * carried in {@link Match#variant()}.
 * <ul>
 * <li>chat ({@code POST /model/{modelId}/converse}, {@code API_runtime_Converse}): request {@code system} (the
 * {@code text} of the {@code SystemContentBlock}s), {@code messages} of {@code ContentBlock}s ({@code text},
 * {@code toolUse{toolUseId, name, input}}, and {@code toolResult{toolUseId, content[{text}|{json}]}}, each tool result
 * read as a {@code tool} message), {@code inferenceConfig{maxTokens, temperature, topP}},
 * {@code toolConfig{tools[{toolSpec{name, description, inputSchema{json}}}], toolChoice{auto|any|tool{name}}}} and
 * {@code outputConfig.textFormat} ({@code type}, {@code structure.jsonSchema.schema}, a JSON string); response
 * {@code output.message{role, content[]}} with {@code reasoningContent.reasoningText}, {@code text} and
 * {@code toolUse} blocks, {@code stopReason}, {@code usage{inputTokens, outputTokens, totalTokens}} plus
 * {@code cacheReadInputTokens}/{@code cacheWriteInputTokens} when the reply has them, and {@code metrics.latencyMs}</li>
 * <li>streaming chat ({@code POST /model/{modelId}/converse-stream}, {@code API_runtime_ConverseStream}):
 * {@code application/vnd.amazon.eventstream} messages ({@link Rendered.StreamFormat#AWS_EVENT_STREAM}) of the
 * {@code ConverseStreamOutput} events {@code messageStart{role}}, {@code contentBlockStart{start{toolUse{toolUseId,
 * name}}, contentBlockIndex}} (tool use only, the start union has no text member), {@code contentBlockDelta{delta,
 * contentBlockIndex}} with {@code text}, {@code reasoningContent{text}} or {@code toolUse{input}} (the arguments JSON as a
 * string), {@code contentBlockStop{contentBlockIndex}}, {@code messageStop{stopReason}} and
 * {@code metadata{usage, metrics}}. Thinking, text and every tool use are content blocks of their own, in that
 * order.</li>
 * <li>embeddings ({@code POST /model/{modelId}/invoke}, {@code API_runtime_InvokeModel}), told apart by the body:
 * <ul>
 * <li>Cohere Embed v3 (<a href=
 * "https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-embed-v3.html">https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-embed-v3.html</a>):
 * request {@code texts}, {@code input_type}, {@code truncate}, {@code embedding_types}; response {@code id},
 * {@code texts} and either {@code response_type: embeddings_floats} with {@code embeddings} an array of float arrays,
 * or, when {@code embedding_types} is given, {@code response_type: embeddings_by_type} with {@code embeddings} an object
 * holding the {@code float} arrays (the page's example reads {@code embeddings} by type then; the response type values
 * are those of the Cohere Embed API reference it links,
 * <a href="https://docs.cohere.com/v1/reference/embed">https://docs.cohere.com/v1/reference/embed</a>)</li>
 * <li>Titan Text Embeddings
 * (<a href=
 * "https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-titan-embed-text.html">https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-titan-embed-text.html</a>):
 * request
 * {@code inputText}, one input per request; response {@code embedding} and {@code inputTextTokenCount} (here the
 * number of whitespace separated words), plus {@code embeddingsByType.float} for the V2 models
 * ({@code titan-embed-text-v2})</li>
 * </ul>
 * </li>
 * <li>errors: REST-JSON errors (<a href=
 * "https://smithy.io/2.0/aws/protocols/aws-restjson1-protocol.html">https://smithy.io/2.0/aws/protocols/aws-restjson1-protocol.html</a>):
 * the body
 * {@code {"message": ...}} and the error shape name in the {@code x-amzn-ErrorType} header. The shape is the service
 * model's exception for the status ({@code ValidationException} 400, {@code AccessDeniedException} 403,
 * {@code ResourceNotFoundException} 404, {@code ModelTimeoutException} 408, {@code ModelErrorException} 424,
 * {@code ThrottlingException} 429, {@code InternalServerException} 500, {@code ServiceUnavailableException} 503),
 * {@code NotAuthorized} for 401 (a common error,
 * <a href=
 * "https://docs.aws.amazon.com/bedrock/latest/APIReference/CommonErrors.html">https://docs.aws.amazon.com/bedrock/latest/APIReference/CommonErrors.html</a>),
 * else {@code ValidationException} for a
 * 4xx and {@code InternalServerException} for a 5xx status.</li>
 * </ul>
 * The documented stop reasons used are {@code end_turn}, {@code tool_use}, {@code max_tokens} and
 * {@code content_filtered}. The SigV4 {@code Authorization} header is accepted without verification.
 * <p>
 * Not modelled, left to raw stubs: other model invocations ({@code invoke} with a model's native request body,
 * {@code invoke-with-response-stream}, {@code invoke-with-bidirectional-stream}), Titan multimodal embeddings,
 * {@code ApplyGuardrail}, {@code CountTokens} and the asynchronous invocation operations.
 */
public class BedrockDialect implements Dialect {

    private static final Pattern MODEL_OPERATION = Pattern.compile("/model/([^/]+)/(converse|converse-stream|invoke)");
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String ERROR_TYPE = "x-amzn-ErrorType";

    private final ObjectMapper mapper = MockApiJson.mapper();

    @Override
    public String id() {
        return "bedrock";
    }

    @Override
    public String pathPrefix() {
        return "/bedrock";
    }

    @Override
    public Optional<Match> match(String method, String path, Map<String, List<String>> query, JsonNode bodyOrNull) {
        Matcher matcher = MODEL_OPERATION.matcher(path);
        if (!"POST".equals(method) || !matcher.matches()) {
            return Optional.empty();
        }
        String modelId = URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
        return switch (matcher.group(2)) {
            case "converse" -> Optional.of(new Match(Operation.CHAT, false, modelId));
            case "converse-stream" -> Optional.of(new Match(Operation.CHAT, true, modelId));
            default -> isEmbeddingBody(bodyOrNull) ? Optional.of(new Match(Operation.EMBEDDINGS, false, modelId))
                    : Optional.empty();
        };
    }

    private static boolean isEmbeddingBody(JsonNode body) {
        return body != null && (body.path("texts").isArray() || body.path("inputText").isTextual());
    }

    @Override
    public ChatRequestView parseChat(Match m, JsonNode body) {
        List<ChatRequestView.Message> messages = new ArrayList<>();
        String system = text(body.path("system"));
        if (system != null) {
            messages.add(new MessageImpl("system", system, null, null, null));
        }
        for (JsonNode msg : body.path("messages")) {
            parseMessage(msg, messages);
        }
        List<ChatRequestView.ToolDefinition> tools = new ArrayList<>();
        for (JsonNode tool : body.path("toolConfig").path("tools")) {
            JsonNode spec = tool.get("toolSpec");
            if (spec != null) {
                JsonNode schema = spec.path("inputSchema").get("json");
                tools.add(new ToolDefinitionImpl(Json.text(spec, "name"), Json.text(spec, "description"), schema));
            }
        }
        JsonNode inference = body.path("inferenceConfig");
        JsonNode textFormat = body.path("outputConfig").path("textFormat");
        return new ChatRequestViewImpl(
                m.variant(),
                m.streaming(),
                inference.hasNonNull("temperature") ? inference.get("temperature").asDouble() : null,
                inference.hasNonNull("topP") ? inference.get("topP").asDouble() : null,
                inference.hasNonNull("maxTokens") ? inference.get("maxTokens").asInt() : null,
                messages,
                tools,
                toolChoice(body.path("toolConfig").get("toolChoice")),
                Json.text(textFormat, "type"),
                schema(textFormat.path("structure").path("jsonSchema").get("schema")));
    }

    /**
     * The message's text and tool uses, then one {@code tool} message per tool result.
     */
    private void parseMessage(JsonNode msg, List<ChatRequestView.Message> messages) {
        List<ToolCall> toolCalls = new ArrayList<>();
        List<ChatRequestView.Message> toolResults = new ArrayList<>();
        for (JsonNode block : msg.path("content")) {
            JsonNode toolUse = block.get("toolUse");
            if (toolUse != null) {
                toolCalls.add(ToolCall.of(Json.text(toolUse, "toolUseId"), Json.text(toolUse, "name"),
                        toolUse.path("input").toString()));
            }
            JsonNode toolResult = block.get("toolResult");
            if (toolResult != null) {
                toolResults.add(new MessageImpl("tool", toolResultText(toolResult.path("content")), null,
                        Json.text(toolResult, "toolUseId"), null));
            }
        }
        String text = text(msg.path("content"));
        if (toolResults.isEmpty() || text != null || !toolCalls.isEmpty()) {
            messages.add(new MessageImpl(Json.text(msg, "role"), text, toolCalls.isEmpty() ? null : toolCalls, null, null));
        }
        messages.addAll(toolResults);
    }

    /**
     * The concatenated {@code text} of the blocks, or null when none has one.
     */
    private static String text(JsonNode blocks) {
        StringBuilder sb = null;
        for (JsonNode block : blocks) {
            if (block.hasNonNull("text")) {
                sb = sb == null ? new StringBuilder() : sb;
                sb.append(block.get("text").asText());
            }
        }
        return sb == null ? null : sb.toString();
    }

    /**
     * The {@code text} blocks of a tool result, or its {@code json} blocks as JSON.
     */
    private static String toolResultText(JsonNode blocks) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : blocks) {
            if (block.hasNonNull("text")) {
                sb.append(block.get("text").asText());
            } else if (block.has("json")) {
                sb.append(block.get("json").toString());
            }
        }
        return sb.toString();
    }

    /**
     * {@code tool} gives the tool's name, the other choices their member name.
     */
    private static String toolChoice(JsonNode choice) {
        if (choice == null || !choice.fieldNames().hasNext()) {
            return null;
        }
        String member = choice.fieldNames().next();
        return "tool".equals(member) ? Json.text(choice.get("tool"), "name") : member;
    }

    private JsonNode schema(JsonNode schemaString) {
        if (schemaString == null || !schemaString.isTextual()) {
            return null;
        }
        try {
            return mapper.readTree(schemaString.asText());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Rendered render(Match m, Reply reply, CapturedRequest req) {
        if (reply instanceof ChatReply chat && m.operation() == Operation.CHAT) {
            return m.streaming() ? renderConverseStream(chat) : renderConverse(chat);
        }
        if (reply instanceof EmbeddingReply embeddings && m.operation() == Operation.EMBEDDINGS) {
            JsonNode body = req.bodyAsJson();
            return body.has("texts") ? renderCohere(embeddings, body, req) : renderTitan(m, embeddings, body);
        }
        throw UnrenderableReply.unsupported("bedrock", m, reply);
    }

    private Rendered renderConverse(ChatReply reply) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode message = root.putObject("output").putObject("message");
        message.put("role", "assistant");
        ArrayNode content = message.putArray("content");
        if (reply.thinking() != null) {
            content.addObject().putObject("reasoningContent").putObject("reasoningText").put("text", reply.thinking());
        }
        if (reply.text() != null && !reply.text().isEmpty()) {
            content.addObject().put("text", reply.text());
        }
        List<ToolCall> toolCalls = toolCalls(reply);
        for (int i = 0; i < toolCalls.size(); i++) {
            ToolCall tc = toolCalls.get(i);
            ObjectNode toolUse = content.addObject().putObject("toolUse");
            toolUse.put("toolUseId", toolUseId(tc, i));
            toolUse.put("name", tc.name());
            toolUse.set("input", Json.arguments(tc));
        }
        root.put("stopReason", stopReason(reply.finishReason()));
        root.set("usage", usage(reply.usage()));
        root.putObject("metrics").put("latencyMs", 0);
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    private Rendered renderConverseStream(ChatReply reply) {
        List<SseEvent> events = new ArrayList<>();
        events.add(event("messageStart", mapper.createObjectNode().put("role", "assistant")));
        int index = 0;
        if (reply.thinking() != null) {
            ObjectNode delta = mapper.createObjectNode();
            delta.putObject("reasoningContent").put("text", reply.thinking());
            events.add(delta(index, delta));
            events.add(blockStop(index++));
        }
        List<String> chunks = Chunker.chunks(reply);
        if (!chunks.isEmpty()) {
            for (String chunk : chunks) {
                events.add(delta(index, mapper.createObjectNode().put("text", chunk)));
            }
            events.add(blockStop(index++));
        }
        List<ToolCall> toolCalls = toolCalls(reply);
        for (int i = 0; i < toolCalls.size(); i++) {
            ToolCall tc = toolCalls.get(i);
            ObjectNode start = mapper.createObjectNode();
            start.putObject("start").putObject("toolUse").put("toolUseId", toolUseId(tc, i)).put("name", tc.name());
            start.put("contentBlockIndex", index);
            events.add(event("contentBlockStart", start));
            ObjectNode delta = mapper.createObjectNode();
            delta.putObject("toolUse").put("input", Json.arguments(tc).toString());
            events.add(delta(index, delta));
            events.add(blockStop(index++));
        }
        events.add(event("messageStop", mapper.createObjectNode().put("stopReason", stopReason(reply.finishReason()))));
        ObjectNode metadata = mapper.createObjectNode();
        metadata.set("usage", usage(reply.usage()));
        metadata.putObject("metrics").put("latencyMs", 0);
        events.add(event("metadata", metadata));
        return Rendered.stream(Rendered.StreamFormat.AWS_EVENT_STREAM, events);
    }

    private SseEvent delta(int index, ObjectNode delta) {
        ObjectNode event = mapper.createObjectNode();
        event.set("delta", delta);
        event.put("contentBlockIndex", index);
        return event("contentBlockDelta", event);
    }

    private SseEvent blockStop(int index) {
        return event("contentBlockStop", mapper.createObjectNode().put("contentBlockIndex", index));
    }

    private static SseEvent event(String type, ObjectNode payload) {
        return SseEvent.of(type, payload.toString());
    }

    private static List<ToolCall> toolCalls(ChatReply reply) {
        return reply.toolCalls() == null ? List.of() : reply.toolCalls();
    }

    private static String toolUseId(ToolCall tc, int index) {
        return tc.id() != null ? tc.id() : "tooluse_mock_" + index;
    }

    private ObjectNode usage(Usage usage) {
        int in = usage == null ? 0 : usage.inputTokens();
        int out = usage == null ? 0 : usage.outputTokens();
        ObjectNode node = mapper.createObjectNode().put("inputTokens", in).put("outputTokens", out)
                .put("totalTokens", in + out);
        if (usage != null && usage.cacheReadTokens() != null) {
            node.put("cacheReadInputTokens", usage.cacheReadTokens());
        }
        if (usage != null && usage.cacheCreationTokens() != null) {
            node.put("cacheWriteInputTokens", usage.cacheCreationTokens());
        }
        return node;
    }

    private static String stopReason(FinishReason reason) {
        return switch (reason) {
            case STOP -> "end_turn";
            case TOOL_CALLS -> "tool_use";
            case LENGTH -> "max_tokens";
            case CONTENT_FILTER -> "content_filtered";
        };
    }

    /**
     * Only float embeddings are rendered, whatever {@code embedding_types} asks for. Token usage is not rendered: the
     * client reads it from a response header that Bedrock does not document.
     */
    private Rendered renderCohere(EmbeddingReply reply, JsonNode body, CapturedRequest req) {
        List<String> texts = new ArrayList<>();
        body.path("texts").forEach(t -> texts.add(t.asText()));
        List<float[]> vectors = DerivedEmbeddings.vectors(reply, texts);
        ObjectNode root = mapper.createObjectNode();
        root.put("id", "mock-embed-" + req.index());
        ArrayNode embeddings;
        if (body.has("embedding_types")) {
            root.put("response_type", "embeddings_by_type");
            embeddings = root.putObject("embeddings").putArray("float");
        } else {
            root.put("response_type", "embeddings_floats");
            embeddings = root.putArray("embeddings");
        }
        vectors.forEach(v -> DerivedEmbeddings.floats(embeddings.addArray(), v));
        ArrayNode textsNode = root.putArray("texts");
        texts.forEach(textsNode::add);
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    private Rendered renderTitan(Match m, EmbeddingReply reply, JsonNode body) {
        String input = body.path("inputText").asText("");
        float[] vector = DerivedEmbeddings.vectors(reply, List.of(input)).get(0);
        ObjectNode root = mapper.createObjectNode();
        DerivedEmbeddings.floats(root.putArray("embedding"), vector);
        root.put("inputTextTokenCount", input.isBlank() ? 0 : input.trim().split("\\s+").length);
        if (m.variant().contains("titan-embed-text-v2")) {
            DerivedEmbeddings.floats(root.putObject("embeddingsByType").putArray("float"), vector);
        }
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    @Override
    public Rendered renderError(Match m, int status, String message) {
        ObjectNode root = mapper.createObjectNode().put("message", message);
        return new Rendered(status, Map.of(CONTENT_TYPE, "application/json", ERROR_TYPE, errorType(status)),
                MockApiJson.toBytes(root), List.of(), Rendered.StreamFormat.NONE);
    }

    private static String errorType(int status) {
        return switch (status) {
            case 400 -> "ValidationException";
            case 401 -> "NotAuthorized";
            case 403 -> "AccessDeniedException";
            case 404 -> "ResourceNotFoundException";
            case 408 -> "ModelTimeoutException";
            case 424 -> "ModelErrorException";
            case 429 -> "ThrottlingException";
            case 503 -> "ServiceUnavailableException";
            default -> status < 500 ? "ValidationException" : "InternalServerException";
        };
    }
}
