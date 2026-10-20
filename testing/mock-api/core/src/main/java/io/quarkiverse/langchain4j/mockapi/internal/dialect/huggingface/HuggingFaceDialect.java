package io.quarkiverse.langchain4j.mockapi.internal.dialect.huggingface;

import static io.quarkiverse.langchain4j.mockapi.internal.dialect.Json.text;

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
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.Reply;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.DerivedEmbeddings;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Json;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.ChatRequestViewImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.MessageImpl;

/**
 * Hugging Face inference endpoints. The provider posts to a full endpoint URL per model kind, which the mock replaces
 * with {@code /huggingface/chat} and {@code /huggingface/embeddings}. Sources:
 * <ul>
 * <li>text generation: the Text Generation Inference OpenAPI ({@code huggingface/text-generation-inference},
 * {@code docs/openapi.json}), operation {@code compat_generate} ({@code POST /}, the path an endpoint URL reaches):
 * request {@code inputs} and {@code parameters} ({@code temperature}, {@code top_p}, {@code max_new_tokens},
 * {@code return_full_text}, ...), response an array of {@code GenerateResponse} with the required
 * {@code generated_text}; errors {@code ErrorResponse} {@code {"error", "error_type"}} with the documented types
 * {@code validation} (422), {@code generation} (424), {@code overloaded} (429) and {@code incomplete_generation} (500).
 * The Inference Providers task page <a href=
 * "https://huggingface.co/docs/inference-providers/tasks/text-generation">https://huggingface.co/docs/inference-providers/tasks/text-generation</a>
 * documents the
 * same request and the single object of {@code POST /generate}; the provider's client reads the array.
 * {@code return_full_text: true} prepends {@code inputs} to the generated text ("Whether to prepend the prompt to the
 * generated text"). The task returns text only: a reply with tool calls is answered with a 500 error</li>
 * <li>feature extraction: <a href=
 * "https://huggingface.co/docs/inference-providers/tasks/feature-extraction">https://huggingface.co/docs/inference-providers/tasks/feature-extraction</a>:
 * request
 * {@code inputs} (a string or an array of strings), response an array of arrays, one vector per input; errors as in
 * the Text Embeddings Inference OpenAPI ({@code huggingface/text-embeddings-inference}, {@code docs/openapi.json},
 * {@code POST /embed}) with the types {@code empty} (400), {@code validation} (413), {@code tokenizer} (422),
 * {@code backend} (424) and {@code overloaded} (429)</li>
 * </ul>
 * An error status without a documented type gets {@code generation} (text generation) or {@code backend} (feature
 * extraction). Streaming is not supported: the provider's client does not stream.
 */
public class HuggingFaceDialect implements Dialect {

    private final ObjectMapper mapper = MockApiJson.mapper();

    @Override
    public String id() {
        return "huggingface";
    }

    @Override
    public String pathPrefix() {
        return "/huggingface";
    }

    @Override
    public Optional<Match> match(String method, String path, Map<String, List<String>> query, JsonNode bodyOrNull) {
        if (!"POST".equals(method)) {
            return Optional.empty();
        }
        return switch (path) {
            case "/chat" -> Optional.of(new Match(Operation.CHAT, false, "text-generation"));
            case "/embeddings" -> Optional.of(new Match(Operation.EMBEDDINGS, false, "feature-extraction"));
            default -> Optional.empty();
        };
    }

    /**
     * The client flattens the conversation into one prompt, so the view has a single message whose role is always
     * {@code user}, and no model name.
     */
    @Override
    public ChatRequestView parseChat(Match m, JsonNode body) {
        JsonNode parameters = body.path("parameters");
        return new ChatRequestViewImpl(
                null,
                false,
                parameters.hasNonNull("temperature") ? parameters.get("temperature").asDouble() : null,
                parameters.hasNonNull("top_p") ? parameters.get("top_p").asDouble() : null,
                parameters.hasNonNull("max_new_tokens") ? parameters.get("max_new_tokens").asInt() : null,
                List.of(new MessageImpl("user", text(body, "inputs"), null, null, null)),
                List.of(),
                null,
                null,
                null);
    }

    @Override
    public Rendered render(Match m, Reply reply, CapturedRequest req) {
        if (reply instanceof ChatReply chat && m.operation() == Operation.CHAT) {
            return renderChat(chat, req);
        }
        if (reply instanceof EmbeddingReply embeddings && m.operation() == Operation.EMBEDDINGS) {
            return renderEmbeddings(embeddings, req);
        }
        throw UnrenderableReply.unsupported("Hugging Face", m, reply);
    }

    private Rendered renderChat(ChatReply reply, CapturedRequest req) {
        if (reply.toolCalls() != null && !reply.toolCalls().isEmpty()) {
            throw new UnrenderableReply("Hugging Face text generation cannot return tool calls");
        }
        JsonNode body = req.bodyAsJson();
        String text = reply.text() == null ? "" : reply.text();
        if (body.path("parameters").path("return_full_text").asBoolean(false)) {
            text = body.path("inputs").asText() + text;
        }
        ArrayNode root = mapper.createArrayNode();
        root.addObject().put("generated_text", text);
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    private Rendered renderEmbeddings(EmbeddingReply reply, CapturedRequest req) {
        List<String> inputs = Json.strings(req.bodyAsJson().get("inputs"));
        ArrayNode root = mapper.createArrayNode();
        for (float[] vector : DerivedEmbeddings.vectors(reply, inputs)) {
            DerivedEmbeddings.floats(root.addArray(), vector);
        }
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    @Override
    public Rendered renderError(Match m, int status, String message) {
        ObjectNode root = mapper.createObjectNode();
        root.put("error", message);
        root.put("error_type",
                m.operation() == Operation.EMBEDDINGS ? embeddingErrorType(status) : generationErrorType(status));
        return Rendered.json(status, MockApiJson.toBytes(root));
    }

    private static String generationErrorType(int status) {
        return switch (status) {
            case 422 -> "validation";
            case 429 -> "overloaded";
            case 500 -> "incomplete_generation";
            default -> "generation";
        };
    }

    private static String embeddingErrorType(int status) {
        return switch (status) {
            case 400 -> "empty";
            case 413 -> "validation";
            case 422 -> "tokenizer";
            case 429 -> "overloaded";
            default -> "backend";
        };
    }
}
