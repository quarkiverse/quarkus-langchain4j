package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_COS_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Date;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationService;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.QuarkusUnitTest;

public class TextClassificationInjectTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.cos-url", URL_COS_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.document-reference.connection",
                    "document-connection")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.document-reference.bucket-name",
                    "document-bucket-name")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.log-requests",
                    "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.log-responses",
                    "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.text-classification.cos-url", URL_COS_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.text-classification.document-reference.connection",
                    "custom-document-connection")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.text-classification.document-reference.bucket-name",
                    "custom-document-bucket-name")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.text-classification.log-requests",
                    "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.text-classification.log-responses",
                    "true")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType().orElse(null))
                .response(BEARER_TOKEN, new Date())
                .build();
    }

    @Inject
    TextClassificationService textClassification;

    @Inject
    @ModelName("custom")
    TextClassificationService customTextClassification;

    @Test
    void textClassificationPropertiesTest() {
        var textClassificationConfig = langchain4jWatsonConfig.defaultConfig().textClassification().orElseThrow();
        assertEquals(false, textClassificationConfig.logRequests().orElse(false));
        assertEquals(false, textClassificationConfig.logResponses().orElse(false));
    }

    @Test
    void customTextClassificationPropertiesTest() {
        var textClassificationConfig = langchain4jWatsonConfig.namedConfig().get("custom").textClassification().orElseThrow();
        assertEquals(true, textClassificationConfig.logRequests().orElse(false));
        assertEquals(true, textClassificationConfig.logResponses().orElse(false));
    }

    @Test
    void textClassificationTest() throws Exception {
        assertNotNull(textClassification);
        assertNotNull(customTextClassification);
    }
}
