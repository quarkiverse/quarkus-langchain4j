package io.quarkiverse.langchain4j.typesafe.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.listener.DecisionModelErrorContext;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.listener.DecisionModelRequestContext;
import dev.langchain4j.model.decision.listener.DecisionModelResponseContext;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.typesafe.TypeSafeDecisionModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.DecisionReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Mirrors the request / response scenarios of the upstream {@code TypeSafeDecisionModelTest}, but going through the
 * CDI bean, the Quarkus configuration and the Quarkus HTTP client.
 */
public class TypeSafeDecisionModelTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(RecordingListener.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.typesafe.api-key", "default-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.typesafe.other.api-key", "other-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.typesafe.other.decision-model.model-name", "jev-1.13.0");

    private static final DecisionReply REPLY = DecisionReply.of(
            DecisionReply.choice("team", "billing", Map.of("billing", 0.88, "support", 0.12), 0.81),
            DecisionReply.predicate("urgent", 0.95),
            DecisionReply.score("frustration", 1.44,
                    List.of(DecisionReply.level(0, "Calm", 0.06), DecisionReply.level(1, "Frustrated", 0.44),
                            DecisionReply.level(2, "Angry", 0.5)),
                    0.78))
            .modelName("jev-1.13.0")
            .usage(318, 34);

    private static final DecisionRequest REQUEST = DecisionRequest.builder()
            .input(Map.of("ticket", "Help! My payouts have been failing for 3 days."))
            .question(
                    "team",
                    ChoiceQuestion.builder()
                            .text("Which team should handle this?")
                            .option("billing", "Payments, invoicing, refunds")
                            .option("support", "Bugs. Not for invoices")
                            .build())
            .question(
                    "urgent",
                    YesNoQuestion.builder()
                            .text("Does this need attention today?")
                            .yesWhen("Money is not reaching the customer")
                            .build())
            .question(
                    "frustration",
                    ScaleQuestion.builder()
                            .text("How frustrated is the customer?")
                            .level("Calm")
                            .level("Frustrated")
                            .level("Angry")
                            .build())
            .build();

    @Inject
    DecisionModel defaultModel;

    @Inject
    @ModelName("other")
    DecisionModel otherModel;

    @Inject
    @ModelName("selfhosted")
    DecisionModel selfHostedModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setUp() {
        mock.reset();
        RecordingListener.clear();
    }

    @Test
    void should_send_request_in_system_one_format() throws JsonProcessingException {
        assertThat(ClientProxy.unwrap(defaultModel)).isInstanceOf(TypeSafeDecisionModel.class);
        mock.decision().reply(REPLY);

        defaultModel.decide(REQUEST);

        CapturedRequest request = singleSystemOneRequest();
        assertThat(request.header("Authorization")).isEqualTo("Bearer default-key");
        assertThat(request.bodyAsJson()).isEqualTo(new ObjectMapper().readTree("""
                {
                  "model": "jev-latest",
                  "state": {"ticket": "Help! My payouts have been failing for 3 days."},
                  "questions": {
                    "team": {
                      "type": "choice",
                      "instructions": "Which team should handle this?",
                      "criteria": {
                        "billing": "Payments, invoicing, refunds",
                        "support": "Bugs. Not for invoices"
                      }
                    },
                    "urgent": {
                      "type": "noul",
                      "instructions": "Does this need attention today?",
                      "criteria": {"true": "Money is not reaching the customer", "false": ""}
                    },
                    "frustration": {
                      "type": "score",
                      "instructions": "How frustrated is the customer?",
                      "criteria": ["Calm", "Frustrated", "Angry"]
                    }
                  }
                }
                """));
    }

    @Test
    void should_map_response() {
        mock.decision().reply(REPLY);

        DecisionResponse response = defaultModel.decide(REQUEST);

        singleSystemOneRequest();
        assertThat(response.answers().keySet()).containsExactly("team", "urgent", "frustration");
        assertThat(response.answers().get("team"))
                .isEqualTo(ChoiceAnswer.builder()
                        .value("billing")
                        .probability("billing", 0.88)
                        .probability("support", 0.12)
                        .confidence(0.81)
                        .options(List.of("billing", "support"))
                        .build());
        assertThat(response.answers().get("urgent"))
                .isEqualTo(YesNoAnswer.builder().probability(0.95).build());
        assertThat(response.answers().get("frustration"))
                .isEqualTo(ScaleAnswer.builder()
                        .mean(1.44)
                        .probabilities(List.of(0.06, 0.44, 0.5))
                        .confidence(0.78)
                        .build());
        assertThat(response.modelName()).isEqualTo("jev-1.13.0");
        assertThat(response.tokenUsage()).isEqualTo(new TokenUsage(318, 34));
    }

    @Test
    void should_use_named_configuration() {
        mock.decision().reply(REPLY);

        otherModel.decide(REQUEST);

        CapturedRequest request = singleSystemOneRequest();
        assertThat(request.header("Authorization")).isEqualTo("Bearer other-key");
        assertThat(request.bodyAsJson().at("/model").asText()).isEqualTo("jev-1.13.0");
    }

    @Test
    void request_model_name_should_override_default() {
        mock.decision().reply(REPLY);

        defaultModel.decide(REQUEST.toBuilder()
                .parameters(DecisionRequestParameters.builder().modelName("jev-1.12.0").build())
                .build());

        CapturedRequest request = singleSystemOneRequest();
        assertThat(request.bodyAsJson().at("/model").asText()).isEqualTo("jev-1.12.0");
    }

    @Test
    void should_not_require_api_key_for_other_servers() {
        mock.decision().reply(REPLY);

        selfHostedModel.decide(REQUEST);

        CapturedRequest request = singleSystemOneRequest();
        assertThat(request.header("Authorization")).isNull();
    }

    @Test
    void should_decide_async() throws Exception {
        mock.decision().reply(REPLY);

        DecisionResponse response = defaultModel.decideAsync(REQUEST).get(10, TimeUnit.SECONDS);

        assertThat(response.choice("team").value()).isEqualTo("billing");
        singleSystemOneRequest();
    }

    @Test
    void should_map_http_errors_without_retrying() {
        mock.decision().fail(401, "unauthorized");

        assertThatThrownBy(() -> defaultModel.decide(REQUEST)).isInstanceOf(AuthenticationException.class);

        singleSystemOneRequest();
        assertThat(RecordingListener.ERRORS).hasSize(1);
    }

    @Test
    void should_not_retry_server_errors() {
        mock.decision().fail(500, "internal error");

        assertThatThrownBy(() -> defaultModel.decide(REQUEST)).isInstanceOf(RuntimeException.class);

        singleSystemOneRequest();
    }

    @Test
    void should_notify_listeners_and_report_provider() {
        mock.decision().reply(REPLY);

        DecisionResponse response = defaultModel.decide(REQUEST);

        singleSystemOneRequest();
        assertThat(RecordingListener.REQUESTS).hasSize(1);
        assertThat(RecordingListener.REQUESTS.get(0).modelProvider()).isEqualTo(ModelProvider.TYPESAFE);
        assertThat(RecordingListener.RESPONSES).hasSize(1);
        assertThat(RecordingListener.RESPONSES.get(0).decisionResponse()).isEqualTo(response);
        assertThat(RecordingListener.ERRORS).isEmpty();
    }

    /**
     * The only request the mock received, after checking it went to the System One endpoint without a query string.
     */
    private CapturedRequest singleSystemOneRequest() {
        assertThat(mock.requests()).hasSize(1);
        CapturedRequest request = mock.requests().get(0);
        assertThat(request.path()).isEqualTo("/typesafe/v1/systemone");
        assertThat(request.query()).isNull();
        return request;
    }

    @ApplicationScoped
    public static class RecordingListener implements DecisionModelListener {

        static final List<DecisionModelRequestContext> REQUESTS = new CopyOnWriteArrayList<>();
        static final List<DecisionModelResponseContext> RESPONSES = new CopyOnWriteArrayList<>();
        static final List<DecisionModelErrorContext> ERRORS = new CopyOnWriteArrayList<>();

        static void clear() {
            REQUESTS.clear();
            RESPONSES.clear();
            ERRORS.clear();
        }

        @Override
        public void onRequest(DecisionModelRequestContext requestContext) {
            REQUESTS.add(requestContext);
        }

        @Override
        public void onResponse(DecisionModelResponseContext responseContext) {
            RESPONSES.add(responseContext);
        }

        @Override
        public void onError(DecisionModelErrorContext errorContext) {
            ERRORS.add(errorContext);
        }
    }
}
