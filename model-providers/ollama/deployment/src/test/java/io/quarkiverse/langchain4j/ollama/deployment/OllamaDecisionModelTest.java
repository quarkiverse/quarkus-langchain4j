package io.quarkiverse.langchain4j.ollama.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
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

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
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
import dev.langchain4j.model.ollama.OllamaDecisionModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Exercises {@code OllamaDecisionModel} through the CDI bean, the Quarkus configuration and the Quarkus HTTP client.
 */
public class OllamaDecisionModelTest extends WiremockAware {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(RecordingListener.class))
            .overrideConfigKey("quarkus.langchain4j.ollama.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideConfigKey("quarkus.langchain4j.ollama.m1.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideConfigKey("quarkus.langchain4j.ollama.m1.decision-model.model-id", "tev1")
            .overrideConfigKey("quarkus.langchain4j.devservices.enabled", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.log-responses", "true");

    private static final String RESPONSE = """
            {
              "model": "nimble",
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
                  "probabilities": {"0": 0.06, "1": 0.44, "2": 0.5},
                  "confidence": 0.78
                }
              },
              "usage": {"input_tokens": 318, "output_tokens": 34}
            }
            """;

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
    DecisionModel defaultModel;

    @Inject
    @ModelName("m1")
    DecisionModel namedModel;

    @BeforeEach
    void setUp() {
        resetRequests();
        resetMappings();
        RecordingListener.clear();
    }

    @Test
    void should_send_request_in_system_one_api_format() {
        assertThat(ClientProxy.unwrap(defaultModel)).isInstanceOf(OllamaDecisionModel.class);
        wiremock().register(systemOne()
                .withRequestBody(equalToJson(
                        """
                                {
                                  "model": "nimble",
                                  "state": "Help! My payouts have been failing for 3 days.",
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
                                      "instructions": "Does this need attention today?\\nAnswer yes when: Money is not reaching the customer"
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
        ChoiceAnswer team = response.choice("team");
        assertThat(team.value()).isEqualTo("billing");
        assertThat(team.probabilities()).isEqualTo(Map.of("billing", 0.88, "support", 0.12));
        assertThat(team.confidence()).isEqualTo(0.81);
        assertThat(response.yesNo("urgent").probability()).isEqualTo(0.95);
        ScaleAnswer frustration = response.scale("frustration");
        assertThat(frustration.mean()).isEqualTo(1.44);
        assertThat(frustration.probabilities()).isEqualTo(List.of(0.06, 0.44, 0.5));
        assertThat(frustration.confidence()).isEqualTo(0.78);
        assertThat(response.modelName()).isEqualTo("nimble");
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(318);
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(34);
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(352);
    }

    @Test
    void should_send_images_separately_from_the_state() {
        wiremock().register(systemOne()
                .withRequestBody(equalToJson("""
                        {
                          "model": "nimble",
                          "state": "The customer attached this photo.\\n\\n[image 1]",
                          "images": ["iVBORw0KGgo="],
                          "questions": {
                            "damaged": {"type": "noul", "instructions": "Is the item visibly damaged?"}
                          }
                        }
                        """))
                .willReturn(ok("""
                        {"model": "nimble", "answers": {"damaged": {"type": "noul", "noul": 0.2}}}
                        """)));

        DecisionResponse response = defaultModel.decide(DecisionRequest.builder()
                .inputContents(
                        TextContent.from("The customer attached this photo."),
                        ImageContent.from("iVBORw0KGgo=", "image/png"))
                .question("damaged", YesNoQuestion.from("Is the item visibly damaged?"))
                .build());

        assertThat(response.yesNo("damaged").probability()).isEqualTo(0.2);
    }

    @Test
    void should_use_named_configuration() {
        assertThat(ClientProxy.unwrap(namedModel)).isInstanceOf(OllamaDecisionModel.class);
        wiremock().register(systemOne()
                .withRequestBody(matchingJsonPath("$.model", equalTo("tev1")))
                .willReturn(ok()));

        namedModel.decide(REQUEST);

        assertThat(wiremock().getServeEvents()).hasSize(1);
    }

    @Test
    void request_model_name_should_override_default() {
        wiremock().register(systemOne()
                .withRequestBody(matchingJsonPath("$.model", equalTo("clef")))
                .willReturn(ok()));

        defaultModel.decide(REQUEST.toBuilder()
                .parameters(DecisionRequestParameters.builder().modelName("clef").build())
                .build());

        assertThat(wiremock().getServeEvents()).hasSize(1);
    }

    @Test
    void should_decide_async() throws Exception {
        wiremock().register(systemOne().willReturn(ok()));

        DecisionResponse response = defaultModel.decideAsync(REQUEST).get(10, TimeUnit.SECONDS);

        assertThat(response.choice("team").value()).isEqualTo("billing");
        assertThat(RecordingListener.RESPONSES).hasSize(1);
    }

    @Test
    void should_notify_listeners_and_report_provider() {
        wiremock().register(systemOne().willReturn(ok()));

        DecisionResponse response = defaultModel.decide(REQUEST);

        assertThat(RecordingListener.REQUESTS).hasSize(1);
        assertThat(RecordingListener.REQUESTS.get(0).modelProvider()).isEqualTo(ModelProvider.OLLAMA);
        assertThat(RecordingListener.RESPONSES).hasSize(1);
        assertThat(RecordingListener.RESPONSES.get(0).decisionResponse()).isEqualTo(response);
        assertThat(RecordingListener.ERRORS).isEmpty();
    }

    @Test
    void should_retry_server_errors_according_to_max_retries() {
        wiremock().register(systemOne().willReturn(aResponse().withStatus(500).withBody("boom")));

        assertThatThrownBy(() -> defaultModel.decide(REQUEST)).isInstanceOf(RuntimeException.class);

        // the max-retries is 1, so the first attempt is retried once
        assertThat(wiremock().getServeEvents()).hasSize(2);
        assertThat(RecordingListener.ERRORS).hasSize(1);
    }

    private static MappingBuilder systemOne() {
        return post(urlEqualTo("/v1/systemone"));
    }

    private static ResponseDefinitionBuilder ok() {
        return ok(RESPONSE);
    }

    private static ResponseDefinitionBuilder ok(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
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
