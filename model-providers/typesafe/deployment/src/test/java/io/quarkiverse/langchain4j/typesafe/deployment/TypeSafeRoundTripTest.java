package io.quarkiverse.langchain4j.typesafe.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.DecisionReply;
import io.quarkiverse.langchain4j.mockapi.DecisionRequestView;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class TypeSafeRoundTripTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.decision-model.provider", "typesafe");

    private static final DecisionRequest REQUEST = DecisionRequest.builder()
            .input(Map.of("ticket", "Help! My payouts have been failing for 3 days."))
            .question("team", ChoiceQuestion.builder()
                    .text("Which team should handle this?")
                    .option("billing", "Payments, invoicing, refunds")
                    .option("support", "Bugs. Not for invoices")
                    .build())
            .question("urgent", YesNoQuestion.builder()
                    .text("Does this need attention today?")
                    .yesWhen("Money is not reaching the customer")
                    .build())
            .question("frustration", ScaleQuestion.builder()
                    .text("How frustrated is the customer?")
                    .level("Calm")
                    .level("Frustrated")
                    .level("Angry")
                    .build())
            .build();

    @Inject
    DecisionModel model;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void choicePredicateAndScoreRoundTrip() {
        mock.decision().reply(DecisionReply.of(
                DecisionReply.choice("team", "billing", Map.of("billing", 0.88, "support", 0.12), 0.81),
                DecisionReply.predicate("urgent", 0.95),
                DecisionReply.score("frustration", 1.44,
                        List.of(DecisionReply.level(0, "Calm", 0.06), DecisionReply.level(1, "Frustrated", 0.44),
                                DecisionReply.level(2, "Angry", 0.5)),
                        0.78))
                .usage(318, 34));

        DecisionResponse resp = model.decide(REQUEST);

        assertThat(resp.choice("team").value()).isEqualTo("billing");
        assertThat(resp.choice("team").confidence()).isEqualTo(0.81);
        assertThat(resp.yesNo("urgent").probability()).isEqualTo(0.95);
        assertThat(resp.scale("frustration").mean()).isEqualTo(1.44);
        assertThat(resp.scale("frustration").probabilities()).containsExactly(0.06, 0.44, 0.5);
        assertThat(resp.modelName()).isEqualTo("jev-latest");
        assertThat(resp.tokenUsage().totalTokenCount()).isEqualTo(352);

        CapturedRequest r = mock.decision().singleRequest();
        assertThat(r.path()).isEqualTo("/typesafe/v1/systemone");
        assertThat(r.dialect()).isEqualTo("typesafe");
        // the api-key is only required for the real service, so none is filled in
        assertThat(r.header("Authorization")).isNull();
        DecisionRequestView v = r.asDecision();
        assertThat(v.modelName()).isEqualTo("jev-latest");
        assertThat(v.inputText()).contains("payouts have been failing");
        assertThat(v.questions()).extracting(DecisionRequestView.Question::name).containsExactly("team", "urgent",
                "frustration");
        assertThat(v.questions().get(0).choices()).containsExactly("billing", "support");
        assertThat(v.questions().get(2).levels()).containsExactly("Calm", "Frustrated", "Angry");
    }

    @Test
    void declinedAnswerIsMissingForTheClient() {
        mock.decision().reply(DecisionReply.of(
                DecisionReply.declined("team"),
                DecisionReply.predicate("urgent", 0.95),
                DecisionReply.score("frustration", 1.0, null, null)));

        // System One has no refusal: the client rejects a response without an answer to a question
        assertThatThrownBy(() -> model.decide(REQUEST))
                .isInstanceOf(InvalidDecisionResponseException.class)
                .hasMessageContaining("no answer to question 'team'");
    }

    @Test
    void errorIsNotRetried() {
        mock.decision().fail(401, "unauthorized");

        assertThatThrownBy(() -> model.decide(REQUEST)).isInstanceOf(AuthenticationException.class);
        assertThat(mock.decision().requests()).hasSize(1);
    }
}
