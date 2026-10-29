package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
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
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.ContentFilteredException;
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
import dev.langchain4j.model.openai.OpenAiDecisionModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Exercises {@code OpenAiDecisionModel} through the CDI bean, the Quarkus configuration and the Quarkus HTTP client.
 */
public class OpenAiDecisionModelTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(RecordingListener.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url", WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "default-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.m1.base-url", WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.m1.api-key", "other-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.m1.decision-model.model-name", "gpt-6-luna-mini");

    private static final String RESPONSE = """
            {
              "model": "gpt-6-luna-2026-09-01",
              "answers": [
                {
                  "type": "choice",
                  "name": "team",
                  "choice": "billing",
                  "probabilities": [
                    {"value": "billing", "probability": 0.88},
                    {"value": "support", "probability": 0.12}
                  ],
                  "confidence": 0.81
                },
                {"type": "predicate", "name": "urgent", "probability": 0.95},
                {
                  "type": "score",
                  "name": "frustration",
                  "score": 1.44,
                  "probabilities": [
                    {"value": 0, "label": "Calm", "probability": 0.06},
                    {"value": 1, "label": "Frustrated", "probability": 0.44},
                    {"value": 2, "label": "Angry", "probability": 0.5}
                  ],
                  "confidence": 0.78
                }
              ],
              "usage": {"input_tokens": 318, "output_tokens": 34, "total_tokens": 352}
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
    void should_send_request_in_decisions_api_format() {
        assertThat(ClientProxy.unwrap(defaultModel)).isInstanceOf(OpenAiDecisionModel.class);
        wiremock().register(decisions()
                .withHeader("Authorization", equalTo("Bearer default-key"))
                .withRequestBody(equalToJson(
                        """
                                {
                                  "model": "gpt-6-luna",
                                  "input": "Help! My payouts have been failing for 3 days.",
                                  "questions": [
                                    {
                                      "type": "choice",
                                      "name": "team",
                                      "instructions": "Which team should handle this?",
                                      "choices": [
                                        {"value": "billing", "description": "Payments, invoicing, refunds"},
                                        {"value": "support", "description": "Bugs. Not for invoices"}
                                      ]
                                    },
                                    {
                                      "type": "predicate",
                                      "name": "urgent",
                                      "instructions": "Does this need attention today?\\nAnswer yes when: Money is not reaching the customer"
                                    },
                                    {
                                      "type": "score",
                                      "name": "frustration",
                                      "instructions": "How frustrated is the customer?",
                                      "levels": [{"label": "Calm"}, {"label": "Frustrated"}, {"label": "Angry"}]
                                    }
                                  ]
                                }
                                """))
                .willReturn(ok()));

        defaultModel.decide(REQUEST);

        assertThat(wiremock().getServeEvents()).hasSize(1);
    }

    @Test
    void should_map_response() {
        wiremock().register(decisions().willReturn(ok()));

        DecisionResponse response = defaultModel.decide(REQUEST);

        assertThat(response.answers().keySet()).containsExactly("team", "urgent", "frustration");
        assertThat(response.choice("team"))
                .isEqualTo(ChoiceAnswer.builder()
                        .value("billing")
                        .probability("billing", 0.88)
                        .probability("support", 0.12)
                        .confidence(0.81)
                        .options(List.of("billing", "support"))
                        .build());
        assertThat(response.yesNo("urgent")).isEqualTo(YesNoAnswer.from(0.95));
        assertThat(response.scale("frustration"))
                .isEqualTo(ScaleAnswer.builder()
                        .mean(1.44)
                        .probabilities(List.of(0.06, 0.44, 0.5))
                        .confidence(0.78)
                        .build());
        assertThat(response.modelName()).isEqualTo("gpt-6-luna-2026-09-01");
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(318);
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(34);
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(352);
    }

    @Test
    void should_map_refusal() {
        wiremock().register(decisions().willReturn(ok("""
                {
                  "model": "gpt-6-luna",
                  "answers": [
                    {"type": "refusal", "name": "team"},
                    {"type": "predicate", "name": "urgent", "probability": 0.95},
                    {"type": "score", "name": "frustration", "score": 1.0}
                  ]
                }
                """)));

        DecisionResponse response = defaultModel.decide(REQUEST);

        assertThat(response.isRefused("team")).isTrue();
        assertThat(response.isRefused("urgent")).isFalse();
        assertThatThrownBy(() -> response.choice("team")).isInstanceOf(ContentFilteredException.class);
        assertThat(response.yesNo("urgent").probability()).isEqualTo(0.95);
    }

    @Test
    void should_send_images_as_input_parts() {
        wiremock().register(decisions()
                .withRequestBody(equalToJson("""
                        {
                          "model": "gpt-6-luna",
                          "input": [
                            {
                              "type": "message",
                              "role": "user",
                              "content": [
                                {"type": "input_text", "text": "The customer attached this photo."},
                                {"type": "input_image", "image_url": "data:image/png;base64,iVBORw0KGgo=", "detail": "low"}
                              ]
                            }
                          ],
                          "questions": [
                            {"type": "predicate", "name": "damaged", "instructions": "Is the item visibly damaged?"}
                          ]
                        }
                        """))
                .willReturn(ok("""
                        {"model": "gpt-6-luna", "answers": [{"type": "predicate", "name": "damaged", "probability": 0.2}]}
                        """)));

        DecisionResponse response = defaultModel.decide(DecisionRequest.builder()
                .inputContents(List.of(
                        TextContent.from("The customer attached this photo."),
                        ImageContent.from("iVBORw0KGgo=", "image/png", ImageContent.DetailLevel.LOW)))
                .question("damaged", YesNoQuestion.from("Is the item visibly damaged?"))
                .build());

        assertThat(response.yesNo("damaged").probability()).isEqualTo(0.2);
    }

    @Test
    void should_use_named_configuration() {
        assertThat(ClientProxy.unwrap(namedModel)).isInstanceOf(OpenAiDecisionModel.class);
        wiremock().register(decisions()
                .withHeader("Authorization", equalTo("Bearer other-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("gpt-6-luna-mini")))
                .willReturn(ok()));

        namedModel.decide(REQUEST);

        assertThat(wiremock().getServeEvents()).hasSize(1);
    }

    @Test
    void request_model_name_should_override_default() {
        wiremock().register(decisions()
                .withRequestBody(matchingJsonPath("$.model", equalTo("gpt-6-luna-2026-09-01")))
                .willReturn(ok()));

        defaultModel.decide(REQUEST.toBuilder()
                .parameters(DecisionRequestParameters.builder().modelName("gpt-6-luna-2026-09-01").build())
                .build());

        assertThat(wiremock().getServeEvents()).hasSize(1);
    }

    @Test
    void should_decide_async() throws Exception {
        wiremock().register(decisions().willReturn(ok()));

        DecisionResponse response = defaultModel.decideAsync(REQUEST).get(10, TimeUnit.SECONDS);

        assertThat(response.choice("team").value()).isEqualTo("billing");
        assertThat(RecordingListener.RESPONSES).hasSize(1);
    }

    @Test
    void should_notify_listeners_and_report_provider() {
        wiremock().register(decisions().willReturn(ok()));

        DecisionResponse response = defaultModel.decide(REQUEST);

        assertThat(RecordingListener.REQUESTS).hasSize(1);
        assertThat(RecordingListener.REQUESTS.get(0).modelProvider()).isEqualTo(ModelProvider.OPEN_AI);
        assertThat(RecordingListener.RESPONSES).hasSize(1);
        assertThat(RecordingListener.RESPONSES.get(0).decisionResponse()).isEqualTo(response);
        assertThat(RecordingListener.ERRORS).isEmpty();
    }

    @Test
    void should_map_authentication_errors_without_retrying() {
        wiremock().register(decisions()
                .willReturn(aResponse().withStatus(401).withBody("{\"error\": \"unauthorized\"}")));

        assertThatThrownBy(() -> defaultModel.decide(REQUEST)).isInstanceOf(AuthenticationException.class);

        assertThat(wiremock().getServeEvents()).hasSize(1);
        assertThat(RecordingListener.ERRORS).hasSize(1);
    }

    @Test
    void should_retry_server_errors_according_to_max_retries() {
        wiremock().register(decisions().willReturn(aResponse().withStatus(500).withBody("boom")));

        assertThatThrownBy(() -> defaultModel.decide(REQUEST)).isInstanceOf(RuntimeException.class);

        // the default max-retries is 1, so the first attempt is retried once
        assertThat(wiremock().getServeEvents()).hasSize(2);
        assertThat(RecordingListener.ERRORS).hasSize(1);
    }

    private static MappingBuilder decisions() {
        return post(urlEqualTo("/v1/decisions"));
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
