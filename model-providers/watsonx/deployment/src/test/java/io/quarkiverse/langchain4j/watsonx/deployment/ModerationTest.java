package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_MODERATION_API;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.detection.DetectionTextRequest;
import com.ibm.watsonx.ai.detection.detector.GraniteGuardian;
import com.ibm.watsonx.ai.detection.detector.Hap;
import com.ibm.watsonx.ai.detection.detector.Pii;

import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.model.moderation.ModerationModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ModerationReply;
import io.quarkus.test.QuarkusExtensionTest;

public class ModerationTest extends WatsonxMockTestBase {

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
}
