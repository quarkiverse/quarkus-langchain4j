package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.COS_PATH;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.PROJECT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationService;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.QuarkusExtensionTest;

public class TextClassificationInjectTest extends WatsonxMockTestBase {

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.cos-url",
                    "${quarkus.langchain4j.mock-api.url}" + COS_PATH)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.document-reference.connection",
                    "document-connection")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.document-reference.bucket-name",
                    "document-bucket-name")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.log-requests",
                    "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.log-responses",
                    "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.text-classification.cos-url",
                    "${quarkus.langchain4j.mock-api.url}" + COS_PATH)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.text-classification.document-reference.connection",
                    "custom-document-connection")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.text-classification.document-reference.bucket-name",
                    "custom-document-bucket-name")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.text-classification.log-requests",
                    "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.text-classification.log-responses",
                    "true")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(WatsonxTestUtil.class));

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
