package io.quarkiverse.langchain4j.mockapi.internal.dialect.openshiftai;

import static io.quarkiverse.langchain4j.mockapi.internal.dialect.Json.text;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.FinishReason;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.Reply;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.ChatRequestViewImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.MessageImpl;

/**
 * Red Hat OpenShift AI text generation: the Caikit NLP {@code text-generation} task served by the Caikit (TGIS)
 * serving runtime for KServe ({@code POST /api/v1/task/text-generation}; the provider's {@code base-url} ends in
 * {@code /api}). Sources:
 * <ul>
 * <li><a href="https://github.com/caikit/caikit-nlp">https://github.com/caikit/caikit-nlp</a> README: the HTTP endpoint
 * {@code /api/v1/task/text-generation}, the request
 * {@code {"model_id", "inputs"}} and the response
 * {@code {"generated_text", "generated_tokens", "finish_reason", "producer_id": {"name", "version"},
 * "input_token_count", "seed"}}</li>
 * <li>caikit {@code caikit/interfaces/nlp/data_model/text_generation.py}: {@code GeneratedTextResult} and the
 * {@code FinishReason} names ({@code NOT_FINISHED}, {@code MAX_TOKENS}, {@code EOS_TOKEN}, {@code CANCELLED},
 * {@code TIME_LIMIT}, {@code STOP_SEQUENCE}, {@code TOKEN_LIMIT}, {@code ERROR}); caikit-nlp
 * {@code TextGenerationTGIS.run}: the {@code parameters} ({@code max_new_tokens}, {@code temperature},
 * {@code top_p}, ...)</li>
 * <li>caikit {@code caikit/runtime/http_server/http_server.py}: the error body {@code {"details", "code", "id"}}</li>
 * </ul>
 * The Red Hat OpenShift AI model serving documentation was not consulted; the provider's placeholder {@code base-url}
 * ({@code https://dummy.ai/api}) agrees with the {@code /api} prefix. The task returns text
 * only: a reply with tool calls is
 * answered with a 500 error. Finish reasons: {@code STOP} and
 * {@code TOOL_CALLS} become {@code EOS_TOKEN}, {@code LENGTH} {@code MAX_TOKENS}, {@code CONTENT_FILTER} {@code ERROR}.
 */
public class OpenshiftAiDialect implements Dialect {

    private final ObjectMapper mapper = MockApiJson.mapper();

    @Override
    public String id() {
        return "openshift-ai";
    }

    @Override
    public String pathPrefix() {
        return "/openshift-ai";
    }

    @Override
    public Optional<Match> match(String method, String path, Map<String, List<String>> query, JsonNode bodyOrNull) {
        if (!"POST".equals(method) || !"/api/v1/task/text-generation".equals(path)) {
            return Optional.empty();
        }
        return Optional.of(new Match(Operation.CHAT, false, "text-generation"));
    }

    @Override
    public ChatRequestView parseChat(Match m, JsonNode body) {
        JsonNode parameters = body.path("parameters");
        return new ChatRequestViewImpl(
                text(body, "model_id"),
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
        if (!(reply instanceof ChatReply chat) || m.operation() != Operation.CHAT) {
            throw UnrenderableReply.unsupported("OpenShift AI", m, reply);
        }
        if (chat.toolCalls() != null && !chat.toolCalls().isEmpty()) {
            throw new UnrenderableReply("OpenShift AI text generation cannot return tool calls");
        }
        ObjectNode root = mapper.createObjectNode();
        root.put("generated_text", chat.text() == null ? "" : chat.text());
        root.put("generated_tokens", chat.usage() == null ? 0 : chat.usage().outputTokens());
        root.put("finish_reason", finishReason(chat.finishReason()));
        root.putObject("producer_id").put("name", "Text Generation").put("version", "0.1.0");
        root.put("input_token_count", chat.usage() == null ? 0 : chat.usage().inputTokens());
        root.putNull("seed");
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    private static String finishReason(FinishReason reason) {
        return switch (reason) {
            case STOP, TOOL_CALLS -> "EOS_TOKEN";
            case LENGTH -> "MAX_TOKENS";
            case CONTENT_FILTER -> "ERROR";
        };
    }

    @Override
    public Rendered renderError(Match m, int status, String message) {
        ObjectNode root = mapper.createObjectNode();
        root.put("details", message);
        root.put("code", status);
        root.put("id", "mock-error");
        return Rendered.json(status, MockApiJson.toBytes(root));
    }
}
