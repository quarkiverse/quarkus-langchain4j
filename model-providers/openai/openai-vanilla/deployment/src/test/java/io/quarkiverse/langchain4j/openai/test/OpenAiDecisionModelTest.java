package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
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

import com.fasterxml.jackson.databind.ObjectMapper;

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
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.DecisionReply;
import io.quarkiverse.langchain4j.mockapi.ErrorReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Exercises {@code OpenAiDecisionModel} through the CDI bean, the Quarkus configuration and the Quarkus HTTP client.
 */
public class OpenAiDecisionModelTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(RecordingListener.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "default-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.m1.api-key", "other-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.m1.decision-model.model-name", "gpt-6-luna-mini");

    private static final DecisionReply REPLY = DecisionReply.of(
            DecisionReply.choice("team", "billing", probabilities("billing", 0.88, "support", 0.12), 0.81),
            DecisionReply.predicate("urgent", 0.95),
            DecisionReply.score("frustration", 1.44,
                    List.of(DecisionReply.level(0, "Calm", 0.06), DecisionReply.level(1, "Frustrated", 0.44),
                            DecisionReply.level(2, "Angry", 0.5)),
                    0.78))
            .modelName("gpt-6-luna-2026-09-01")
            .usage(318, 34);

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

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setUp() {
        mock.reset();
        RecordingListener.clear();
    }

    @Test
    void should_send_request_in_decisions_api_format() throws Exception {
        assertThat(ClientProxy.unwrap(defaultModel)).isInstanceOf(OpenAiDecisionModel.class);
        mock.decision().reply(REPLY);

        defaultModel.decide(REQUEST);

        CapturedRequest request = singleDecisionRequest();
        assertThat(request.header("Authorization")).isEqualTo("Bearer default-key");
        assertThat(request.bodyAsJson()).isEqualTo(new ObjectMapper().readTree(
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
                        """));
    }

    @Test
    void should_map_response() {
        mock.decision().reply(REPLY);

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
        assertThat(response.yesNo("urgent")).isEqualTo(YesNoAnswer.of(0.95));
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
        singleDecisionRequest();
    }

    @Test
    void should_map_refusal() {
        mock.decision().reply(DecisionReply.of(
                DecisionReply.declined("team"),
                DecisionReply.predicate("urgent", 0.95),
                DecisionReply.score("frustration", 1.0, List.of(), null)));

        DecisionResponse response = defaultModel.decide(REQUEST);

        assertThat(response.isRefused("team")).isTrue();
        assertThat(response.isRefused("urgent")).isFalse();
        assertThatThrownBy(() -> response.choice("team")).isInstanceOf(ContentFilteredException.class);
        assertThat(response.yesNo("urgent").probability()).isEqualTo(0.95);
        singleDecisionRequest();
    }

    @Test
    void should_send_images_as_input_parts() throws Exception {
        mock.decision().reply(DecisionReply.of(DecisionReply.predicate("damaged", 0.2)));

        DecisionResponse response = defaultModel.decide(DecisionRequest.builder()
                .input(List.of(
                        TextContent.from("The customer attached this photo."),
                        ImageContent.from("iVBORw0KGgo=", "image/png", ImageContent.DetailLevel.LOW)))
                .question("damaged", YesNoQuestion.of("Is the item visibly damaged?"))
                .build());

        assertThat(response.yesNo("damaged").probability()).isEqualTo(0.2);
        assertThat(singleDecisionRequest().bodyAsJson()).isEqualTo(new ObjectMapper().readTree("""
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
                """));
    }

    @Test
    void should_use_named_configuration() {
        assertThat(ClientProxy.unwrap(namedModel)).isInstanceOf(OpenAiDecisionModel.class);
        mock.decision().reply(REPLY);

        namedModel.decide(REQUEST);

        CapturedRequest request = singleDecisionRequest();
        assertThat(request.header("Authorization")).isEqualTo("Bearer other-key");
        assertThat(request.bodyAsJson().at("/model").textValue()).isEqualTo("gpt-6-luna-mini");
    }

    @Test
    void request_model_name_should_override_default() {
        mock.decision().reply(REPLY);

        defaultModel.decide(REQUEST.toBuilder()
                .parameters(DecisionRequestParameters.builder().modelName("gpt-6-luna-2026-09-01").build())
                .build());

        assertThat(singleDecisionRequest().bodyAsJson().at("/model").textValue()).isEqualTo("gpt-6-luna-2026-09-01");
    }

    @Test
    void should_decide_async() throws Exception {
        mock.decision().reply(REPLY);

        DecisionResponse response = defaultModel.decideAsync(REQUEST).get(10, TimeUnit.SECONDS);

        assertThat(response.choice("team").value()).isEqualTo("billing");
        assertThat(RecordingListener.RESPONSES).hasSize(1);
        singleDecisionRequest();
    }

    @Test
    void should_notify_listeners_and_report_provider() {
        mock.decision().reply(REPLY);

        DecisionResponse response = defaultModel.decide(REQUEST);

        assertThat(RecordingListener.REQUESTS).hasSize(1);
        assertThat(RecordingListener.REQUESTS.get(0).modelProvider()).isEqualTo(ModelProvider.OPEN_AI);
        assertThat(RecordingListener.RESPONSES).hasSize(1);
        assertThat(RecordingListener.RESPONSES.get(0).decisionResponse()).isEqualTo(response);
        assertThat(RecordingListener.ERRORS).isEmpty();
        singleDecisionRequest();
    }

    @Test
    void should_map_authentication_errors_without_retrying() {
        mock.decision().fail(401, "unauthorized");

        assertThatThrownBy(() -> defaultModel.decide(REQUEST)).isInstanceOf(AuthenticationException.class);

        // a retry would be recorded as a second request
        singleDecisionRequest();
        assertThat(RecordingListener.ERRORS).hasSize(1);
    }

    @Test
    void should_retry_server_errors_according_to_max_retries() {
        mock.decision().reply(ErrorReply.of(500, "boom"), ErrorReply.of(500, "boom"));

        assertThatThrownBy(() -> defaultModel.decide(REQUEST)).isInstanceOf(RuntimeException.class);

        // the default max-retries is 1, so the first attempt is retried once
        assertThat(mock.requests()).hasSize(2);
        assertThat(mock.decision().requests()).hasSize(2).allSatisfy(request -> {
            assertThat(request.path()).isEqualTo("/v1/decisions");
            assertThat(request.query()).isNull();
        });
        assertThat(RecordingListener.ERRORS).hasSize(1);
    }

    private CapturedRequest singleDecisionRequest() {
        assertThat(mock.requests()).hasSize(1);
        CapturedRequest request = mock.decision().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/decisions");
        assertThat(request.query()).isNull();
        return request;
    }

    private static Map<String, Double> probabilities(String firstValue, double firstProbability, String secondValue,
            double secondProbability) {
        Map<String, Double> probabilities = new LinkedHashMap<>();
        probabilities.put(firstValue, firstProbability);
        probabilities.put(secondValue, secondProbability);
        return probabilities;
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
