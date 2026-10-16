package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_MODERATION_API;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.core.auth.Authenticator;
import com.ibm.watsonx.ai.detection.DetectionContextRequest;
import com.ibm.watsonx.ai.detection.DetectionContextResponse;
import com.ibm.watsonx.ai.detection.DetectionGeneratedRequest;
import com.ibm.watsonx.ai.detection.DetectionGeneratedResponse;
import com.ibm.watsonx.ai.detection.DetectionService;
import com.ibm.watsonx.ai.detection.DetectionTextRequest;
import com.ibm.watsonx.ai.detection.detector.AnswerRelevance;
import com.ibm.watsonx.ai.detection.detector.ContextRelevance;
import com.ibm.watsonx.ai.detection.detector.Faithfulness;
import com.ibm.watsonx.ai.detection.detector.GraniteGuardian;
import com.ibm.watsonx.ai.detection.detector.Hap;
import com.ibm.watsonx.ai.detection.detector.Pii;

import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.model.moderation.ModerationModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ModerationReply;
import io.quarkus.test.QuarkusExtensionTest;

public class ModerationTest extends WatsonxMockTestBase {

    private static final Authenticator AUTHENTICATOR = new Authenticator() {
        @Override
        public String token() {
            return BEARER_TOKEN;
        }

        @Override
        public CompletableFuture<String> tokenAsync() {
            return CompletableFuture.completedFuture(BEARER_TOKEN);
        }

        @Override
        public String scheme() {
            return "Bearer";
        }
    };

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.moderation-model.pii.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.moderation-model.hap.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.moderation-model.granite-guardian.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.moderation-model.granite-guardian.threshold", "0.8")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WatsonxTestUtil.class));

    @Inject
    ModerationModel moderationModel;

    @Test
    void should_build_request_with_correct_parameters() throws Exception {

        DetectionTextRequest request = DetectionTextRequest.builder()
                .input("input")
                .detectors(Pii.ofDefaults(), Hap.ofDefaults(), GraniteGuardian.builder().threshold(0.8).build())
                .projectId(PROJECT_ID)
                .build();

        mock.moderation().reply(ModerationReply.flagged("pii"));

        assertTrue(moderationModel.moderate("input").content().flagged());

        CapturedRequest captured = mock.moderation().singleRequest();
        assertWatsonxRequest(captured, URL_WATSONX_MODERATION_API);
        assertJsonBody(captured, request);
    }

    @Test
    void should_catch_watsonx_exception() throws Exception {

        DetectionTextRequest request = DetectionTextRequest.builder()
                .input("input")
                .detectors(Pii.ofDefaults(), Hap.ofDefaults(), GraniteGuardian.builder().threshold(0.8).build())
                .projectId(PROJECT_ID)
                .build();

        mock.moderation().fail(400,
                "Invalid request: `detectors` is a required field and must contain at least one supported detector");

        assertThrows(InvalidRequestException.class, () -> moderationModel.moderate("input"));

        // a 400 is not retried
        CapturedRequest captured = mock.moderation().singleRequest();
        assertWatsonxRequest(captured, URL_WATSONX_MODERATION_API);
        assertJsonBody(captured, request);
    }

    @Test
    void should_detect_context_relevance_and_faithfulness() {
        String path = "/watsonx/ml/v1/text/detection/context";
        String expectedBody = """
                {
                  "input": "What is IBM?",
                  "context_type": "docs",
                  "context": ["https://en.wikipedia.org/wiki/IBM"],
                  "project_id": "%s",
                  "detectors": {
                    "context_relevance": {},
                    "faithfulness": { "threshold": 0.5 }
                  }
                }""".formatted(PROJECT_ID);
        mock.raw().on("POST", path).reply(200, """
                {
                  "detections": [
                    {
                      "detector_id": "context_relevance_125m_v1_en",
                      "detection_type": "context_relevance",
                      "detection": "relevant",
                      "score": 0.87,
                      "evidence": [
                        {
                          "name": "sentence_1",
                          "value": "IBM is a technology company.",
                          "score": 0.92,
                          "evidence": []
                        }
                      ]
                    }
                  ]
                }""");

        DetectionContextRequest request = DetectionContextRequest.builder()
                .input("What is IBM?")
                .context(List.of("https://en.wikipedia.org/wiki/IBM"))
                .detectors(ContextRelevance.ofDefaults(), Faithfulness.ofThreshold(0.5))
                .build();

        List<DetectionContextResponse> detections = detectionService().detectContext(request).detections();
        assertThat(detections).hasSize(1);

        DetectionContextResponse detection = detections.get(0);
        assertThat(detection.detectorId()).isEqualTo("context_relevance_125m_v1_en");
        assertThat(detection.detectionType()).isEqualTo("context_relevance");
        assertThat(detection.detection()).isEqualTo("relevant");
        assertThat(detection.score()).isCloseTo(0.87, within(0.001));
        assertThat(detection.evidence()).hasSize(1);
        assertThat(detection.evidence().get(0).name()).isEqualTo("sentence_1");
        assertThat(requests("POST", path)).hasSize(1);
        assertWatsonxRequest(requests("POST", path).get(0), path, BEARER_TOKEN);
        assertJsonBody(requests("POST", path).get(0), expectedBody);
    }

    @Test
    void should_detect_answer_relevance_in_generated_text() {
        String path = "/watsonx/ml/v1/text/detection/generated";
        String expectedBody = """
                {
                  "prompt": "What is IBM?",
                  "generated_text": "IBM is a technology company.",
                  "project_id": "%s",
                  "detectors": {
                    "answer_relevance": {}
                  }
                }""".formatted(PROJECT_ID);
        mock.raw().on("POST", path).reply(200, """
                {
                  "detections": [
                    {
                      "detector_id": "answer_relevance_125m_v1_en",
                      "detection_type": "answer_relevance",
                      "detection": "relevant",
                      "score": 0.91
                    }
                  ]
                }""");

        DetectionGeneratedRequest request = DetectionGeneratedRequest.builder()
                .prompt("What is IBM?")
                .generatedText("IBM is a technology company.")
                .detectors(AnswerRelevance.ofDefaults())
                .build();

        List<DetectionGeneratedResponse> detections = detectionService().detectGenerated(request).detections();
        assertThat(detections).hasSize(1);

        DetectionGeneratedResponse detection = detections.get(0);
        assertThat(detection.detectorId()).isEqualTo("answer_relevance_125m_v1_en");
        assertThat(detection.detectionType()).isEqualTo("answer_relevance");
        assertThat(detection.detection()).isEqualTo("relevant");
        assertThat(detection.score()).isCloseTo(0.91, within(0.001));
        assertThat(requests("POST", path)).hasSize(1);
        assertWatsonxRequest(requests("POST", path).get(0), path, BEARER_TOKEN);
        assertJsonBody(requests("POST", path).get(0), expectedBody);
    }

    private DetectionService detectionService() {
        return DetectionService.builder()
                .authenticator(AUTHENTICATOR)
                .baseUrl(langchain4jWatsonConfig.defaultConfig().baseUrl().orElseThrow())
                .projectId(PROJECT_ID)
                .build();
    }
}
