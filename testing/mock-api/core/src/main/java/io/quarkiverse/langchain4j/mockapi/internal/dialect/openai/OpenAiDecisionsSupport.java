package io.quarkiverse.langchain4j.mockapi.internal.dialect.openai;

import static io.quarkiverse.langchain4j.mockapi.internal.dialect.Json.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.DecisionReply;
import io.quarkiverse.langchain4j.mockapi.DecisionRequestView;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Json;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.DecisionRequestViewImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.QuestionImpl;

/**
 * Request parsing and response rendering for the OpenAI Decisions API ({@code /decisions}).
 */
final class OpenAiDecisionsSupport {

    private static final Set<String> INPUT_PARTS = Set.of("input_text", "output_text");

    private final ObjectMapper mapper = MockApiJson.mapper();

    DecisionRequestView parse(JsonNode body) {
        List<DecisionRequestView.Question> questions = new ArrayList<>();
        for (JsonNode q : body.path("questions")) {
            List<String> choices = null;
            if (q.path("choices").isArray()) {
                choices = new ArrayList<>();
                for (JsonNode c : q.get("choices")) {
                    choices.add(text(c, "value"));
                }
            }
            List<String> levels = null;
            if (q.path("levels").isArray()) {
                levels = new ArrayList<>();
                for (JsonNode l : q.get("levels")) {
                    levels.add(text(l, "label"));
                }
            }
            questions.add(new QuestionImpl(text(q, "type"), text(q, "name"),
                    text(q, "instructions"), choices, levels));
        }
        return new DecisionRequestViewImpl(text(body, "model"), inputText(body.get("input")), questions);
    }

    private static String inputText(JsonNode input) {
        if (input == null || input.isNull()) {
            return null;
        }
        if (input.isTextual()) {
            return input.asText();
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : input) {
            String text = Json.contentText(item.get("content"), INPUT_PARTS);
            if (text != null) {
                sb.append(text);
            }
        }
        return sb.toString();
    }

    Rendered render(DecisionReply reply, CapturedRequest req) {
        JsonNode body = req.bodyAsJson();
        ObjectNode root = mapper.createObjectNode();
        root.put("model", reply.modelName() != null ? reply.modelName()
                : Json.requestModel(req, "mock-decision-model"));
        ArrayNode answers = root.putArray("answers");
        if (reply.answers() == null || reply.answers().isEmpty()) {
            for (JsonNode q : body.path("questions")) {
                refusal(answers, text(q, "name"));
            }
        } else {
            for (DecisionReply.Answer a : reply.answers()) {
                answer(answers, a);
            }
        }
        OpenAiJson.responsesUsage(root, reply.usage());
        return Rendered.json(200, MockApiJson.toBytes(root));
    }

    private void answer(ArrayNode answers, DecisionReply.Answer a) {
        if (a instanceof DecisionReply.Predicate p) {
            ObjectNode n = answers.addObject();
            n.put("type", "predicate");
            putName(n, p.name());
            n.put("probability", p.probability());
        } else if (a instanceof DecisionReply.Choice c) {
            ObjectNode n = answers.addObject();
            n.put("type", "choice");
            putName(n, c.name());
            n.put("choice", c.choice());
            ArrayNode probabilities = n.putArray("probabilities");
            if (c.probabilities() != null) {
                for (Map.Entry<String, Double> e : c.probabilities().entrySet()) {
                    probabilities.addObject().put("value", e.getKey()).put("probability", e.getValue());
                }
            }
            n.put("confidence", c.confidence() != null ? c.confidence() : 0.0);
        } else if (a instanceof DecisionReply.Score s) {
            ObjectNode n = answers.addObject();
            n.put("type", "score");
            putName(n, s.name());
            n.put("score", s.score());
            ArrayNode probabilities = n.putArray("probabilities");
            if (s.probabilities() != null) {
                for (DecisionReply.Score.Level l : s.probabilities()) {
                    probabilities.addObject().put("value", l.value()).put("label", l.label()).put("probability",
                            l.probability());
                }
            }
            n.put("confidence", s.confidence() != null ? s.confidence() : 0.0);
        } else {
            refusal(answers, a.name());
        }
    }

    // the spec calls the declined answer "refusal"
    private static void refusal(ArrayNode answers, String name) {
        ObjectNode n = answers.addObject();
        n.put("type", "refusal");
        putName(n, name);
    }

    private static void putName(ObjectNode n, String name) {
        if (name == null) {
            n.putNull("name");
        } else {
            n.put("name", name);
        }
    }
}
