package io.quarkiverse.langchain4j.mockapi.internal.dialect.gemini;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.Reply;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.DerivedEmbeddings;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;

/**
 * Gemini on Vertex AI as spoken by {@code quarkus-langchain4j-vertex-ai-gemini}: the Gemini request and response bodies of
 * {@link GeminiDialect} under the Vertex AI resource path
 * {@code POST /v1/projects/{project}/locations/{location}/publishers/{publisher}/models/{model}:generateContent} and
 * {@code :streamGenerateContent?alt=sse}, plus text embeddings through {@code :predict}. Sources:
 * <ul>
 * <li><a href=
 * "https://cloud.google.com/vertex-ai/generative-ai/docs/model-reference/inference">https://cloud.google.com/vertex-ai/generative-ai/docs/model-reference/inference</a>
 * and
 * <a href=
 * "https://github.com/googleapis/googleapis/blob/master/google/cloud/aiplatform/v1/prediction_service.proto">https://github.com/googleapis/googleapis/blob/master/google/cloud/aiplatform/v1/prediction_service.proto</a>
 * (HTTP
 * bindings of {@code GenerateContent}, {@code StreamGenerateContent} and {@code Predict} on
 * {@code projects/*&#47;locations/*&#47;publishers/*&#47;models/*}; {@code GenerateContentResponse} with
 * {@code candidates}, {@code usageMetadata}, {@code modelVersion}, {@code responseId}), and {@code tool.proto}, whose
 * Vertex {@code FunctionCall} has only {@code name} and {@code args}: no {@code id} is ever rendered</li>
 * <li><a href=
 * "https://cloud.google.com/vertex-ai/generative-ai/docs/model-reference/text-embeddings-api">https://cloud.google.com/vertex-ai/generative-ai/docs/model-reference/text-embeddings-api</a>:
 * {@code instances[]} of
 * {@code {content, task_type, title}} in, {@code predictions[].embeddings{statistics{truncated, token_count}, values}}
 * out, one prediction per instance</li>
 * </ul>
 * The optional {@code createTime} of the Vertex response is not rendered. {@code token_count} is the number of
 * whitespace separated words of the instance's content.
 */
public class VertexAiGeminiDialect extends GeminiDialect {

    private static final Pattern PATH = Pattern.compile(
            "^/v1/projects/[^/]+/locations/[^/]+/publishers/[^/]+/models/([^/:]+):(generateContent|streamGenerateContent|predict)$");

    public VertexAiGeminiDialect() {
        super("vertexai-gemini", "/vertexai-gemini");
    }

    @Override
    public Optional<Match> match(String method, String path, Map<String, List<String>> query, JsonNode bodyOrNull) {
        Matcher matcher = PATH.matcher(path);
        if (!"POST".equals(method) || !matcher.matches()) {
            return Optional.empty();
        }
        String model = matcher.group(1);
        if ("predict".equals(matcher.group(2))) {
            return Optional.of(new Match(Operation.EMBEDDINGS, false, model));
        }
        return chatMatch(matcher.group(2), model, query);
    }

    @Override
    protected boolean functionCallIds() {
        return false;
    }

    @Override
    public Rendered render(Match m, Reply reply, CapturedRequest req) {
        if (reply instanceof EmbeddingReply embeddings && m.operation() == Operation.EMBEDDINGS) {
            return renderPredict(embeddings, req);
        }
        return super.render(m, reply, req);
    }

    private Rendered renderPredict(EmbeddingReply reply, CapturedRequest req) {
        List<String> inputs = new ArrayList<>();
        req.bodyAsJson().path("instances").forEach(i -> inputs.add(i.path("content").asText()));
        List<float[]> vectors = DerivedEmbeddings.vectors(reply, inputs);
        ObjectNode root = MockApiJson.mapper().createObjectNode();
        ArrayNode predictions = root.putArray("predictions");
        for (int i = 0; i < inputs.size(); i++) {
            ObjectNode embeddings = predictions.addObject().putObject("embeddings");
            String content = inputs.get(i).strip();
            embeddings.putObject("statistics").put("truncated", false)
                    .put("token_count", content.isEmpty() ? 0 : content.split("\\s+").length);
            DerivedEmbeddings.floats(embeddings.putArray("values"), vectors.get(i));
        }
        return Rendered.json(200, MockApiJson.toBytes(root));
    }
}
