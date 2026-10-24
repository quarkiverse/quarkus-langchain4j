package io.quarkiverse.langchain4j.mockapi.internal.dialect.cohere;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.Reply;
import io.quarkiverse.langchain4j.mockapi.ScoringReply;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.DerivedEmbeddings;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;

/**
 * Cohere Rerank API v2, following
 * <a href="https://docs.cohere.com/reference/rerank">https://docs.cohere.com/reference/rerank</a>:
 * <ul>
 * <li>{@code POST /v2/rerank} with {@code model}, {@code query}, {@code documents} and the optional {@code top_n}
 * (default: every document)</li>
 * <li>response {@code id}, {@code results} (most relevant first, each {@code index} into {@code documents} and
 * {@code relevance_score}) and {@code meta} ({@code api_version.version} {@code 2},
 * {@code billed_units.search_units} {@code 1})</li>
 * <li>errors: a JSON object with the optional {@code message} (and {@code id}); only {@code message} is rendered</li>
 * </ul>
 * The reply's scores are given in document order; extra scores are ignored and too few are a 500 error.
 * {@link ScoringReply#equalScores()} gives every document the score {@code 1.0}, keeping document order.
 */
public class CohereDialect implements Dialect {

    private final ObjectMapper mapper = MockApiJson.mapper();

    @Override
    public String id() {
        return "cohere";
    }

    @Override
    public String pathPrefix() {
        return "/cohere";
    }

    @Override
    public Optional<Match> match(String method, String path, Map<String, List<String>> query, JsonNode bodyOrNull) {
        if (!"POST".equals(method) || !"/v2/rerank".equals(path)) {
            return Optional.empty();
        }
        return Optional.of(new Match(Operation.SCORING, false, "rerank"));
    }

    @Override
    public Rendered render(Match m, Reply reply, CapturedRequest req) {
        if (!(reply instanceof ScoringReply scoring) || m.operation() != Operation.SCORING) {
            throw UnrenderableReply.unsupported("Cohere", m, reply);
        }
        JsonNode body = req.bodyAsJson();
        int documents = body.path("documents").size();
        List<Double> scores = DerivedEmbeddings.scores(scoring, documents, "documents");

        List<Integer> ranking = new ArrayList<>();
        for (int i = 0; i < documents; i++) {
            ranking.add(i);
        }
        // stable, so equal scores keep document order
        ranking.sort(Comparator.comparing((Integer i) -> scores.get(i)).reversed());
        int topN = body.hasNonNull("top_n") ? Math.min(body.get("top_n").asInt(), documents) : documents;

        ObjectNode root = mapper.createObjectNode();
        root.put("id", UUID.randomUUID().toString());
        ArrayNode results = root.putArray("results");
        for (int index : ranking.subList(0, topN)) {
            results.addObject().put("index", index).put("relevance_score", scores.get(index));
        }
        ObjectNode meta = root.putObject("meta");
        meta.putObject("api_version").put("version", "2");
        meta.putObject("billed_units").put("search_units", 1);
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    @Override
    public Rendered renderError(Match m, int status, String message) {
        ObjectNode root = mapper.createObjectNode();
        root.put("message", message);
        return Rendered.json(status, MockApiJson.toBytes(root));
    }
}
