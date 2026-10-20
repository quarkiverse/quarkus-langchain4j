package io.quarkiverse.langchain4j.mockapi.internal.dialect.typesafe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.DecisionReply;
import io.quarkiverse.langchain4j.mockapi.DecisionRequestView;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.DialectRegistry;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.CapturedRequestImpl;

class TypeSafeDialectTest {

    private static final Dialect.Match DECIDE = new Dialect.Match(Operation.DECISION, false, "systemone");

    private static final String REQUEST = """
            {"model":"jev-latest",
             "state":{"ticket":"Help! My payouts have been failing for 3 days."},
             "questions":{
               "team":{"type":"choice","instructions":"Which team should handle this?",
                       "criteria":{"billing":"Payments, invoicing, refunds","support":null}},
               "urgent":{"type":"noul","instructions":"Does this need attention today?",
                         "criteria":{"true":"Money is not reaching the customer","false":""}},
               "frustration":{"type":"score","instructions":"How frustrated is the customer?",
                              "criteria":["Calm",{"label":"Frustrated","description":"upset"},"Angry"]}}}
            """;

    TypeSafeDialect d = new TypeSafeDialect();
    ObjectMapper m = MockApiJson.mapper();

    static CapturedRequest req(String body) {
        return new CapturedRequestImpl(1, "POST", "/typesafe/v1/systemone", null, Map.of(), body, Operation.DECISION,
                "typesafe", null, null);
    }

    @Test
    void matchesSystemOneUnderItsPrefix() {
        assertThat(d.id()).isEqualTo("typesafe");
        assertThat(d.pathPrefix()).isEqualTo("/typesafe");
        assertThat(d.match("POST", "/v1/systemone", Map.of(), null)).contains(DECIDE);
        assertThat(d.match("GET", "/v1/systemone", Map.of(), null)).isEmpty();
        assertThat(d.match("POST", "/v1/decisions", Map.of(), null)).isEmpty();
    }

    @Test
    void typeSafeRequestsAreResolvedByPrefix() {
        assertThat(DialectRegistry.load().resolve("POST", "/typesafe/v1/systemone", Map.of(), null))
                .get().extracting(r -> r.dialect().id()).isEqualTo("typesafe");
    }

    @Test
    void parsesRequest() throws Exception {
        DecisionRequestView v = d.parseDecision(DECIDE, m.readTree(REQUEST));

        assertThat(v.modelName()).isEqualTo("jev-latest");
        assertThat(m.readTree(v.inputText()))
                .isEqualTo(m.readTree("{\"ticket\":\"Help! My payouts have been failing for 3 days.\"}"));
        assertThat(v.questions()).extracting(DecisionRequestView.Question::name).containsExactly("team", "urgent",
                "frustration");
        assertThat(v.questions()).extracting(DecisionRequestView.Question::type).containsExactly("choice", "noul",
                "score");
        assertThat(v.questions().get(0).instructions()).isEqualTo("Which team should handle this?");
        assertThat(v.questions().get(0).choices()).containsExactly("billing", "support");
        assertThat(v.questions().get(0).levels()).isNull();
        assertThat(v.questions().get(1).choices()).isNull();
        assertThat(v.questions().get(2).levels()).containsExactly("Calm", "Frustrated", "Angry");
    }

    @Test
    void parsesTextState() throws Exception {
        DecisionRequestView v = d.parseDecision(DECIDE, m.readTree(
                "{\"model\":\"jev-latest\",\"state\":\"hello\",\"questions\":{\"q\":{\"type\":\"noul\",\"instructions\":\"x\"}}}"));

        assertThat(v.inputText()).isEqualTo("hello");
        assertThat(v.questions()).singleElement().satisfies(q -> assertThat(q.name()).isEqualTo("q"));
    }

    @Test
    void rendersEveryAnswerTypeKeyedByQuestion() throws Exception {
        Map<String, Double> probabilities = new LinkedHashMap<>();
        probabilities.put("billing", 0.88);
        probabilities.put("support", 0.12);
        DecisionReply reply = DecisionReply.of(
                DecisionReply.choice("team", "billing", probabilities, 0.81),
                DecisionReply.predicate("urgent", 0.95),
                DecisionReply.score("frustration", 1.44, List.of(DecisionReply.level(0, "Calm", 0.06),
                        DecisionReply.level(1, "Frustrated", 0.44), DecisionReply.level(2, "Angry", 0.5)), 0.78))
                .usage(318, 34);

        Rendered r = d.render(DECIDE, reply, req(REQUEST));

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.headers()).containsEntry("Content-Type", "application/json");
        assertThat(m.readTree(r.body())).isEqualTo(m.readTree("""
                {"model":"jev-latest",
                 "answers":{
                   "team":{"type":"choice","choice":"billing","probabilities":{"billing":0.88,"support":0.12},
                           "confidence":0.81},
                   "urgent":{"type":"noul","noul":0.95},
                   "frustration":{"type":"score","score":1.44,"legend":{"0":"Calm","1":"Frustrated","2":"Angry"},
                                  "probabilities":{"0":0.06,"1":0.44,"2":0.5},"confidence":0.78}},
                 "usage":{"input_tokens":318,"output_tokens":34}}
                """));
    }

    @Test
    void scoreWithoutLevelsTakesTheLegendFromTheRequest() throws Exception {
        Rendered r = d.render(DECIDE, DecisionReply.of(DecisionReply.score("frustration", 2.0, null, null))
                .modelName("jev-1.13.0"), req(REQUEST));

        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/model").asText()).isEqualTo("jev-1.13.0");
        assertThat(j.at("/answers/frustration/legend"))
                .isEqualTo(m.readTree("{\"0\":\"Calm\",\"1\":\"Frustrated\",\"2\":\"Angry\"}"));
        assertThat(j.at("/answers/frustration/probabilities").isObject()).isTrue();
        assertThat(j.at("/answers/frustration/confidence").asDouble()).isEqualTo(0.0);
        assertThat(j.at("/usage/input_tokens").asInt()).isZero();
        assertThat(j.at("/usage/output_tokens").asInt()).isZero();
    }

    @Test
    void declinedAnswersAreLeftOut() throws Exception {
        JsonNode j = m.readTree(d.render(DECIDE,
                DecisionReply.of(DecisionReply.predicate("urgent", 0.1), DecisionReply.declined("team")), req(REQUEST))
                .body());

        assertThat(j.at("/answers").size()).isEqualTo(1);
        assertThat(j.at("/answers").has("urgent")).isTrue();

        JsonNode empty = m.readTree(d.render(DECIDE, DecisionReply.of(), req(REQUEST)).body());
        assertThat(empty.at("/answers").isObject()).isTrue();
        assertThat(empty.at("/answers").size()).isZero();
    }

    @Test
    void anAnswerWithoutANameIsAnError() {
        assertThatThrownBy(() -> d.render(DECIDE, DecisionReply.of(DecisionReply.predicate(null, 0.1)), req(REQUEST)))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("name");
    }

    @Test
    void errorHasAnErrorMessage() throws Exception {
        Rendered r = d.renderError(DECIDE, 401, "unauthorized");

        assertThat(r.status()).isEqualTo(401);
        assertThat(m.readTree(r.body())).isEqualTo(m.readTree("{\"error\":\"unauthorized\"}"));
    }

    @Test
    void unsupportedReplyIsUnrenderable() {
        assertThatThrownBy(() -> d.render(DECIDE, ChatReply.text("x"), req("{}")))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("not supported");
    }
}
