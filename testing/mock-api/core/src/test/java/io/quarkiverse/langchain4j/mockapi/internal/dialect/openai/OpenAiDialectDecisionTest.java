package io.quarkiverse.langchain4j.mockapi.internal.dialect.openai;

import static io.quarkiverse.langchain4j.mockapi.internal.dialect.openai.OpenAiTestRequests.match;
import static io.quarkiverse.langchain4j.mockapi.internal.dialect.openai.OpenAiTestRequests.req;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.mockapi.DecisionReply;
import io.quarkiverse.langchain4j.mockapi.DecisionRequestView;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;

class OpenAiDialectDecisionTest {

    private static final String BODY = """
            {"model":"gpt-6-luna","input":"Help! My payouts have been failing for 3 days.",
             "questions":[{"type":"choice","name":"team","instructions":"Which team?","choices":[{"value":"billing","description":"Payments"},{"value":"support","description":"Bugs"}]},
                          {"type":"predicate","name":"urgent","instructions":"Today?"},
                          {"type":"score","name":"frustration","instructions":"How frustrated?","levels":[{"label":"Calm"},{"label":"Frustrated"},{"label":"Angry"}]}]}""";

    OpenAiDialect d = new OpenAiDialect();
    ObjectMapper m = MockApiJson.mapper();

    @Test
    void matchesDecisions() {
        assertThat(d.match("POST", "/v1/decisions", Map.of(), null))
                .contains(new Dialect.Match(Operation.DECISION, false, "decisions"));
        assertThat(d.match("GET", "/v1/decisions", Map.of(), null)).isEmpty();
    }

    @Test
    void parsesQuestions() throws Exception {
        DecisionRequestView v = d.parseDecision(match(Operation.DECISION, false, "decisions"), m.readTree(BODY));
        assertThat(v.modelName()).isEqualTo("gpt-6-luna");
        assertThat(v.inputText()).startsWith("Help!");
        assertThat(v.questions()).extracting(DecisionRequestView.Question::type).containsExactly("choice", "predicate",
                "score");
        assertThat(v.questions().get(0).choices()).containsExactly("billing", "support");
        assertThat(v.questions().get(2).levels()).containsExactly("Calm", "Frustrated", "Angry");
    }

    @Test
    void parsesInputItems() throws Exception {
        String body = """
                {"model":"gpt-6-luna","input":[{"type":"message","role":"user","content":"Hello "},
                  {"role":"user","content":[{"type":"input_text","text":"there"}]}],
                 "questions":[{"type":"predicate","instructions":"Greeting?"}]}""";
        DecisionRequestView v = d.parseDecision(match(Operation.DECISION, false, "decisions"), m.readTree(body));
        assertThat(v.inputText()).isEqualTo("Hello there");
        assertThat(v.questions().get(0).name()).isNull();
    }

    @Test
    void rendersAllAnswerKinds() throws Exception {
        DecisionReply r = DecisionReply
                .of(DecisionReply.choice("team", "billing", Map.of("billing", 0.88, "support", 0.12), 0.81),
                        DecisionReply.predicate("urgent", 0.95),
                        DecisionReply.score("frustration", 1.44,
                                List.of(DecisionReply.level(0, "Calm", 0.06), DecisionReply.level(1, "Frustrated", 0.44),
                                        DecisionReply.level(2, "Angry", 0.5)),
                                0.78))
                .usage(318, 34);
        Rendered rendered = d.render(match(Operation.DECISION, false, "decisions"), r,
                req("/v1/decisions", Operation.DECISION, "{\"model\":\"gpt-6-luna\"}"));
        JsonNode j = m.readTree(rendered.body());
        assertThat(rendered.status()).isEqualTo(200);
        assertThat(j.at("/model").asText()).isEqualTo("gpt-6-luna");
        assertThat(j.at("/answers/0/type").asText()).isEqualTo("choice");
        assertThat(j.at("/answers/0/name").asText()).isEqualTo("team");
        assertThat(j.at("/answers/0/choice").asText()).isEqualTo("billing");
        assertThat(j.at("/answers/0/confidence").asDouble()).isEqualTo(0.81);
        assertThat(j.at("/answers/0/probabilities")).hasSize(2);
        assertThat(j.at("/answers/0/probabilities").findValuesAsText("value")).containsExactlyInAnyOrder("billing", "support");
        assertThat(j.at("/answers/1/type").asText()).isEqualTo("predicate");
        assertThat(j.at("/answers/1/probability").asDouble()).isEqualTo(0.95);
        assertThat(j.at("/answers/2/score").asDouble()).isEqualTo(1.44);
        assertThat(j.at("/answers/2/probabilities/2/label").asText()).isEqualTo("Angry");
        assertThat(j.at("/answers/2/probabilities/2/value").asInt()).isEqualTo(2);
        assertThat(j.at("/usage/input_tokens").asInt()).isEqualTo(318);
        assertThat(j.at("/usage/output_tokens").asInt()).isEqualTo(34);
        assertThat(j.at("/usage/total_tokens").asInt()).isEqualTo(352);
        assertThat(j.at("/usage/input_tokens_details/cached_tokens").asInt()).isZero();
        assertThat(j.at("/usage/output_tokens_details/reasoning_tokens").asInt()).isZero();
    }

    @Test
    void declinesEveryQuestionWhenNoAnswers() throws Exception {
        Rendered rendered = d.render(match(Operation.DECISION, false, "decisions"), DecisionReply.of(),
                req("/v1/decisions", Operation.DECISION, BODY));
        JsonNode j = m.readTree(rendered.body());
        assertThat(j.at("/answers")).hasSize(3);
        for (int i = 0; i < 3; i++) {
            assertThat(j.at("/answers/" + i + "/type").asText()).isEqualTo("refusal");
        }
        assertThat(j.at("/answers/0/name").asText()).isEqualTo("team");
        assertThat(j.at("/answers/2/name").asText()).isEqualTo("frustration");
    }

    @Test
    void rendersDeclinedAnswerAsRefusal() throws Exception {
        Rendered rendered = d.render(match(Operation.DECISION, false, "decisions"),
                DecisionReply.of(DecisionReply.declined("urgent")),
                req("/v1/decisions", Operation.DECISION, BODY));
        JsonNode j = m.readTree(rendered.body());
        assertThat(j.at("/answers/0/type").asText()).isEqualTo("refusal");
        assertThat(j.at("/answers/0/name").asText()).isEqualTo("urgent");
    }

    @Test
    void rendersErrorEnvelope() throws Exception {
        Rendered rendered = d.renderError(match(Operation.DECISION, false, "decisions"), 429, "slow down");
        assertThat(rendered.status()).isEqualTo(429);
        assertThat(m.readTree(rendered.body()).at("/error/message").asText()).isEqualTo("slow down");
    }
}
