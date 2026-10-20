package io.quarkiverse.langchain4j.mockapi.internal.dialect.vertexai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.Reply;
import io.quarkiverse.langchain4j.mockapi.Usage;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.gemini.GeminiDialect;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.ChatRequestViewImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.MessageImpl;

/**
 * The Vertex AI PaLM chat models ({@code chat-bison}) as spoken by {@code quarkus-langchain4j-vertex-ai}:
 * {@code POST /v1/projects/{project}/locations/{location}/publishers/{publisher}/models/{model}:predict}. Sources:
 * <ul>
 * <li><a href=
 * "https://cloud.google.com/vertex-ai/docs/reference/rest/v1/projects.locations.publishers.models/predict">https://cloud.google.com/vertex-ai/docs/reference/rest/v1/projects.locations.publishers.models/predict</a>
 * and
 * <a href=
 * "https://github.com/googleapis/googleapis/blob/master/google/cloud/aiplatform/v1/prediction_service.proto">https://github.com/googleapis/googleapis/blob/master/google/cloud/aiplatform/v1/prediction_service.proto</a>:
 * the
 * {@code Predict} HTTP binding and its envelope, {@code instances} and {@code parameters} in, {@code predictions} and
 * {@code metadata} out, both model specific</li>
 * <li>the PaLM chat schema, whose reference page (model-reference/text-chat) Google no longer publishes since the
 * models were retired, so it is taken from the client's {@code VertxAiRestApi}, {@code PredictRequest} and
 * {@code PredictResponse} and the captured response in its {@code VertexAiChatLanguageModelSmokeTest}: one instance
 * {@code {context, messages[{author, content}]}}, {@code parameters{temperature, maxOutputTokens, topK, topP}};
 * {@code predictions[{candidates[{author, content}]}]} with the bot's author {@code "1"} and
 * {@code metadata.tokenMetadata.{inputTokenCount,outputTokenCount}.totalTokens}</li>
 * <li>the Google error envelope of <a href="https://google.aip.dev/193">https://google.aip.dev/193</a>, as in
 * {@link GeminiDialect}</li>
 * </ul>
 * PaLM chat has neither streaming nor tools. In the dialect-neutral view {@code context} becomes a {@code system}
 * message, a message whose author is {@code AI} or {@code bot} (any case) an {@code assistant} message and any other
 * a {@code user} message. The model is in the path, so the match variant carries the model id.
 */
public class VertexAiDialect implements Dialect {

    private static final Pattern PATH = Pattern
            .compile("^/v1/projects/[^/]+/locations/[^/]+/publishers/[^/]+/models/([^/:]+):predict$");

    @Override
    public String id() {
        return "vertexai";
    }

    @Override
    public String pathPrefix() {
        return "/vertexai";
    }

    @Override
    public Optional<Match> match(String method, String path, Map<String, List<String>> query, JsonNode bodyOrNull) {
        Matcher matcher = PATH.matcher(path);
        if (!"POST".equals(method) || !matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new Match(Operation.CHAT, false, matcher.group(1)));
    }

    @Override
    public ChatRequestView parseChat(Match m, JsonNode body) {
        JsonNode instance = body.path("instances").path(0);
        List<ChatRequestView.Message> messages = new ArrayList<>();
        String context = instance.path("context").asText("");
        if (!context.isEmpty()) {
            messages.add(new MessageImpl("system", context, null, null, null));
        }
        for (JsonNode message : instance.path("messages")) {
            String author = message.path("author").asText("");
            String role = "ai".equalsIgnoreCase(author) || "bot".equalsIgnoreCase(author) ? "assistant" : "user";
            messages.add(new MessageImpl(role, message.path("content").asText(""), null, null, null));
        }
        JsonNode parameters = body.path("parameters");
        return new ChatRequestViewImpl(
                m.variant(),
                false,
                parameters.hasNonNull("temperature") ? parameters.get("temperature").asDouble() : null,
                parameters.hasNonNull("topP") ? parameters.get("topP").asDouble() : null,
                parameters.hasNonNull("maxOutputTokens") ? parameters.get("maxOutputTokens").asInt() : null,
                messages,
                List.of(),
                null,
                null,
                null);
    }

    @Override
    public Rendered render(Match m, Reply reply, CapturedRequest req) {
        if (!(reply instanceof ChatReply chat) || m.operation() != Operation.CHAT) {
            throw UnrenderableReply.unsupported("Vertex AI predict", m, reply);
        }
        if (chat.toolCalls() != null && !chat.toolCalls().isEmpty()) {
            throw new UnrenderableReply("Vertex AI PaLM chat models have no tool calls");
        }
        ObjectNode root = MockApiJson.mapper().createObjectNode();
        root.putArray("predictions").addObject().putArray("candidates").addObject()
                .put("author", "1")
                .put("content", chat.text() == null ? "" : chat.text());
        Usage usage = chat.usage();
        ObjectNode tokenMetadata = root.putObject("metadata").putObject("tokenMetadata");
        tokenMetadata.putObject("inputTokenCount").put("totalTokens", usage == null ? 0 : usage.inputTokens());
        tokenMetadata.putObject("outputTokenCount").put("totalTokens", usage == null ? 0 : usage.outputTokens());
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    @Override
    public Rendered renderError(Match m, int status, String message) {
        return GeminiDialect.googleError(status, message);
    }
}
