package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_MODERATION_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

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
import io.quarkus.test.QuarkusUnitTest;

public class ModerationTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.moderation-model.pii.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.moderation-model.hap.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.moderation-model.granite-guardian.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.moderation-model.granite-guardian.threshold", "0.8")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Inject
    ModerationModel moderationModel;

    @Override
    void handlerBeforeEach() throws Exception {
        mockIAMBuilder(200)
                .response(BEARER_TOKEN, new Date())
                .build();
    }

    @Test
    void should_build_request_with_correct_parameters() throws Exception {

        DetectionTextRequest request = DetectionTextRequest.builder()
                .input("input")
                .detectors(Pii.ofDefaults(), Hap.ofDefaults(), GraniteGuardian.builder().threshold(0.8).build())
                .projectId(PROJECT_ID)
                .build();

        mockWatsonxBuilder(URL_WATSONX_MODERATION_API, 200)
                .body(mapper.writeValueAsString(request))
                .response("""
                        {
                            "detections": [{
                                "start": 20,
                                "end": 24,
                                "detection_type": "pii",
                                "detection": "xxxx",
                                "score": 0.846
                            }]
                        }""")
                .build();

        assertTrue(moderationModel.moderate("input").content().flagged());
    }

    @Test
    void should_catch_watsonx_exception() throws Exception {

        String EXCEPTION = """
                {
                    "errors": [
                        {
                            "code": "invalid_request_entity",
                            "message": "Invalid request: `detectors` is a required field and must contain at least one supported detector",
                            "more_info": "https://cloud.ibm.com/apidocs/watsonx-ai#text-detection-content"
                        }
                    ],
                    "trace": "004ca103d58aee42ec64f3d7a428b698",
                    "status_code": 400
                }""";

        DetectionTextRequest request = DetectionTextRequest.builder()
                .input("input")
                .detectors(Pii.ofDefaults(), Hap.ofDefaults(), GraniteGuardian.builder().threshold(0.8).build())
                .projectId(PROJECT_ID)
                .build();

        mockWatsonxBuilder(URL_WATSONX_MODERATION_API, 400)
                .body(mapper.writeValueAsString(request))
                .response(EXCEPTION)
                .build();

        assertThrows(InvalidRequestException.class, () -> moderationModel.moderate("input"));
    }
}
