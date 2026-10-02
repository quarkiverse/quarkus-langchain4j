package io.quarkiverse.langchain4j.typesafe.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
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

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

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
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Mirrors the request / response scenarios of the upstream {@code TypeSafeDecisionModelTest}, but going through the
 * CDI bean, the Quarkus configuration and the Quarkus HTTP client.
 */
public class TypeSafeDecisionModelTest extends WiremockAware {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(RecordingListener.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.typesafe.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideRuntimeConfigKey("quarkus.langchain4j.typesafe.api-key", "default-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.typesafe.other.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideRuntimeConfigKey("quarkus.langchain4j.typesafe.other.api-key", "other-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.typesafe.other.decision-model.model-name", "jev-1.13.0")
            .overrideRuntimeConfigKey("quarkus.langchain4j.typesafe.selfhosted.base-url",
                    WiremockAware.wiremockUrlForConfig());

    private static final String RESPONSE = """
            {
              "model": "jev-1.13.0",
              "answers": {
                "team": {
                  "type": "choice",
                  "choice": "billing",
                  "probabilities": {"billing": 0.88, "support": 0.12},
                  "confidence": 0.81
                },
                "urgent": {"type": "noul", "noul": 0.95},
                "frustration": {
                  "type": "score",
                  "score": 1.44,
                  "legend": {"0": "Calm", "1": "Frustrated", "2": "Angry"},
                  "probabilities": {"0": 0.06, "1": 0.44, "2": 0.5},
                  "confidence": 0.78
                }
              },
              "usage": {"input_tokens": 318, "output_tokens": 34},
              "latency_ms": 95
            }
            """;

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

    @BeforeEach
    void setUp() {
        resetRequests();
        resetMappings();
        RecordingListener.clear();
    }

    @Test
    void should_send_request_in_system_one_format() {
        assertThat(ClientProxy.unwrap(defaultModel)).isInstanceOf(TypeSafeDecisionModel.class);
        wiremock().register(systemOne()
                .withHeader("Authorization", equalTo("Bearer default-key"))
                .withRequestBody(equalToJson("""
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
                        """))
                .willReturn(ok()));

        defaultModel.decide(REQUEST);

        assertThat(wiremock().getServeEvents()).hasSize(1);
    }

    @Test
    void should_map_response() {
        wiremock().register(systemOne().willReturn(ok()));

        DecisionResponse response = defaultModel.decide(REQUEST);

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
        wiremock().register(systemOne()
                .withHeader("Authorization", equalTo("Bearer other-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("jev-1.13.0")))
                .willReturn(ok()));

        otherModel.decide(REQUEST);

        assertThat(wiremock().getServeEvents()).hasSize(1);
    }

    @Test
    void request_model_name_should_override_default() {
        wiremock().register(systemOne()
                .withRequestBody(matchingJsonPath("$.model", equalTo("jev-1.12.0")))
                .willReturn(ok()));

        defaultModel.decide(REQUEST.toBuilder()
                .parameters(DecisionRequestParameters.builder().modelName("jev-1.12.0").build())
                .build());

        assertThat(wiremock().getServeEvents()).hasSize(1);
    }

    @Test
    void should_not_require_api_key_for_other_servers() {
        wiremock().register(systemOne()
                .withHeader("Authorization", absent())
                .willReturn(ok()));

        selfHostedModel.decide(REQUEST);

        assertThat(wiremock().getServeEvents()).hasSize(1);
    }

    @Test
    void should_decide_async() throws Exception {
        wiremock().register(systemOne().willReturn(ok()));

        DecisionResponse response = defaultModel.decideAsync(REQUEST).get(10, TimeUnit.SECONDS);

        assertThat(response.choice("team").value()).isEqualTo("billing");
    }

    @Test
    void should_map_http_errors_without_retrying() {
        wiremock().register(systemOne().willReturn(aResponse().withStatus(401).withBody("{\"error\": \"unauthorized\"}")));

        assertThatThrownBy(() -> defaultModel.decide(REQUEST)).isInstanceOf(AuthenticationException.class);

        assertThat(wiremock().getServeEvents()).hasSize(1);
        assertThat(RecordingListener.ERRORS).hasSize(1);
    }

    @Test
    void should_not_retry_server_errors() {
        wiremock().register(systemOne().willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> defaultModel.decide(REQUEST)).isInstanceOf(RuntimeException.class);

        assertThat(wiremock().getServeEvents()).hasSize(1);
    }

    @Test
    void should_notify_listeners_and_report_provider() {
        wiremock().register(systemOne().willReturn(ok()));

        DecisionResponse response = defaultModel.decide(REQUEST);

        assertThat(RecordingListener.REQUESTS).hasSize(1);
        assertThat(RecordingListener.REQUESTS.get(0).modelProvider()).isEqualTo(ModelProvider.TYPESAFE);
        assertThat(RecordingListener.RESPONSES).hasSize(1);
        assertThat(RecordingListener.RESPONSES.get(0).decisionResponse()).isEqualTo(response);
        assertThat(RecordingListener.ERRORS).isEmpty();
    }

    private static MappingBuilder systemOne() {
        return post(urlEqualTo("/v1/systemone"));
    }

    private static ResponseDefinitionBuilder ok() {
        return aResponse().withHeader("Content-Type", "application/json").withBody(RESPONSE);
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
