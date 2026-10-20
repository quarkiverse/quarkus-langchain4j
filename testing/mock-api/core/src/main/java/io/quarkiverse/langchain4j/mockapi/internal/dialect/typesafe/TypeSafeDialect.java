package io.quarkiverse.langchain4j.mockapi.internal.dialect.typesafe;

import static io.quarkiverse.langchain4j.mockapi.internal.dialect.Json.jsonText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.DecisionReply;
import io.quarkiverse.langchain4j.mockapi.DecisionRequestView;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.Reply;
import io.quarkiverse.langchain4j.mockapi.Usage;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Json;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.DecisionRequestViewImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.QuestionImpl;

/**
 * TypeSafe System One API, following the API reference
 * <a href="https://docs.typesafe.ai/api.md">https://docs.typesafe.ai/api.md</a>:
 * <ul>
 * <li>{@code POST /v1/systemone} with {@code model}, {@code state} (a string, object or array) and {@code questions}, a
 * map from question id to {@code {type, instructions, criteria}}; types {@code noul} (criteria {@code true} and
 * {@code false}), {@code choice} (criteria: option to description) and {@code score} (criteria: the ordered
 * levels)</li>
 * <li>response {@code model}, {@code answers} (keyed by question id) and {@code usage} ({@code input_tokens},
 * {@code output_tokens}); answers {@code {type: noul, noul}}, {@code {type: choice, choice, probabilities,
 * confidence}} and {@code {type: score, score, legend, probabilities, confidence}}, where {@code legend} and
 * {@code probabilities} are keyed by the level index as a string</li>
 * </ul>
 * In the neutral view a question's {@code type} is the wire type, its {@code choices} are the choice criteria's keys
 * and its {@code levels} the score criteria (a structured level gives its {@code label}, else its JSON). A
 * {@link DecisionReply.Predicate} renders as a {@code noul} answer. A score's {@code legend} comes from its levels, or
 * from the question's criteria when the reply has none; a missing {@code confidence} renders as {@code 0}. The API has
 * no refusal: a {@link DecisionReply.Declined} answer is left out of {@code answers} (as is every question of an empty
 * reply), which the provider's client reports as a missing answer. The docs give no error body shape ("a JSON body
 * describing what went wrong"): errors render {@code {"error": "..."}}, the body the provider's tests use.
 */
public class TypeSafeDialect implements Dialect {

    private final ObjectMapper mapper = MockApiJson.mapper();

    @Override
    public String id() {
        return "typesafe";
    }

    @Override
    public String pathPrefix() {
        return "/typesafe";
    }

    @Override
    public Optional<Match> match(String method, String path, Map<String, List<String>> query, JsonNode bodyOrNull) {
        if (!"POST".equals(method) || !"/v1/systemone".equals(path)) {
            return Optional.empty();
        }
        return Optional.of(new Match(Operation.DECISION, false, "systemone"));
    }

    @Override
    public DecisionRequestView parseDecision(Match m, JsonNode body) {
        List<DecisionRequestView.Question> questions = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : body.path("questions").properties()) {
            JsonNode q = entry.getValue();
            String type = jsonText(q.get("type"));
            JsonNode criteria = q.path("criteria");
            List<String> choices = null;
            List<String> levels = null;
            if ("choice".equals(type) && criteria.isObject()) {
                choices = new ArrayList<>();
                criteria.fieldNames().forEachRemaining(choices::add);
            } else if ("score".equals(type) && criteria.isArray()) {
                levels = new ArrayList<>();
                for (JsonNode level : criteria) {
                    levels.add(level.hasNonNull("label") ? level.get("label").asText() : jsonText(level));
                }
            }
            questions.add(new QuestionImpl(type, entry.getKey(), jsonText(q.get("instructions")), choices, levels));
        }
        return new DecisionRequestViewImpl(jsonText(body.get("model")), jsonText(body.get("state")), questions);
    }

    @Override
    public Rendered render(Match m, Reply reply, CapturedRequest req) {
        if (!(reply instanceof DecisionReply decision) || m.operation() != Operation.DECISION) {
            throw UnrenderableReply.unsupported("TypeSafe", m, reply);
        }
        JsonNode body = req.bodyAsJson();
        ObjectNode root = mapper.createObjectNode();
        root.put("model", decision.modelName() != null ? decision.modelName()
                : Json.requestModel(req, "mock-decision-model"));
        ObjectNode answers = root.putObject("answers");
        for (DecisionReply.Answer a : decision.answers() == null ? List.<DecisionReply.Answer> of() : decision.answers()) {
            if (a.name() == null) {
                throw new UnrenderableReply("TypeSafe answers are keyed by question: every answer needs a name");
            }
            if (a instanceof DecisionReply.Predicate p) {
                answers.putObject(p.name()).put("type", "noul").put("noul", p.probability());
            } else if (a instanceof DecisionReply.Choice c) {
                ObjectNode n = answers.putObject(c.name()).put("type", "choice").put("choice", c.choice());
                ObjectNode probabilities = n.putObject("probabilities");
                if (c.probabilities() != null) {
                    c.probabilities().forEach(probabilities::put);
                }
                n.put("confidence", c.confidence() != null ? c.confidence() : 0.0);
            } else if (a instanceof DecisionReply.Score s) {
                ObjectNode n = answers.putObject(s.name()).put("type", "score").put("score", s.score());
                ObjectNode legend = n.putObject("legend");
                ObjectNode probabilities = n.putObject("probabilities");
                if (s.probabilities() != null && !s.probabilities().isEmpty()) {
                    for (DecisionReply.Score.Level l : s.probabilities()) {
                        legend.put(String.valueOf(l.value()), l.label());
                        probabilities.put(String.valueOf(l.value()), l.probability());
                    }
                } else {
                    JsonNode criteria = body.path("questions").path(s.name()).path("criteria");
                    for (int i = 0; i < criteria.size(); i++) {
                        JsonNode level = criteria.get(i);
                        legend.put(String.valueOf(i),
                                level.hasNonNull("label") ? level.get("label").asText() : jsonText(level));
                    }
                }
                n.put("confidence", s.confidence() != null ? s.confidence() : 0.0);
            }
            // a declined answer is left out: the API has no refusal
        }
        Usage usage = decision.usage();
        root.putObject("usage")
                .put("input_tokens", usage == null ? 0 : usage.inputTokens())
                .put("output_tokens", usage == null ? 0 : usage.outputTokens());
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    @Override
    public Rendered renderError(Match m, int status, String message) {
        ObjectNode root = mapper.createObjectNode();
        root.put("error", message);
        return Rendered.json(status, MockApiJson.toBytes(root));
    }
}
