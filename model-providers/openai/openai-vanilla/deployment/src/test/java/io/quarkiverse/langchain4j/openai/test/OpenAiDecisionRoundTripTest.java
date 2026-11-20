package io.quarkiverse.langchain4j.openai.test;

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

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import io.quarkiverse.langchain4j.mockapi.DecisionReply;
import io.quarkiverse.langchain4j.mockapi.DecisionRequestView;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class OpenAiDecisionRoundTripTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.decision-model.provider", "openai")
            .overrideConfigKey("quarkus.langchain4j.openai.decision-model.model-name", "gpt-6-luna");

    private static final DecisionRequest REQUEST = DecisionRequest.builder()
            .input("Help! My payouts have been failing for 3 days.")
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
    void decisionsRoundTrip() {
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
        assertThat(resp.yesNo("urgent").probability()).isEqualTo(0.95);
        assertThat(resp.scale("frustration").mean()).isEqualTo(1.44);
        assertThat(resp.scale("frustration").probabilities()).containsExactly(0.06, 0.44, 0.5);
        assertThat(resp.tokenUsage().totalTokenCount()).isEqualTo(352);

        DecisionRequestView v = mock.decision().singleRequest().asDecision();
        assertThat(v.modelName()).isEqualTo("gpt-6-luna");
        assertThat(v.questions()).extracting(DecisionRequestView.Question::name).containsExactly("team", "urgent",
                "frustration");
    }

    @Test
    void emptyReplyRefusesEveryQuestion() {
        mock.decision().reply(DecisionReply.of());

        DecisionResponse resp = model.decide(REQUEST);

        assertThat(resp.isRefused("team")).isTrue();
        assertThat(resp.isRefused("urgent")).isTrue();
        assertThat(resp.isRefused("frustration")).isTrue();
    }

    @Test
    void noReplyConfiguredFails() {
        assertThatThrownBy(() -> model.decide(REQUEST))
                .hasMessageContaining("no reply configured");
    }
}
