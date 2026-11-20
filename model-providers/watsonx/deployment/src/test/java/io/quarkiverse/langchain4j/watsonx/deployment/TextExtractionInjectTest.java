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

import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionService;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.QuarkusExtensionTest;

public class TextExtractionInjectTest extends WatsonxMockTestBase {

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.cos-url",
                    "${quarkus.langchain4j.mock-api.url}" + COS_PATH)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.document-reference.connection",
                    "document-connection")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.document-reference.bucket-name",
                    "document-bucket-name")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.results-reference.connection",
                    "results-connection")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.results-reference.bucket-name",
                    "results-bucket-name")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.log-requests",
                    "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.log-responses",
                    "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.text-extraction.cos-url",
                    "${quarkus.langchain4j.mock-api.url}" + COS_PATH)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.text-extraction.document-reference.connection",
                    "custom-document-connection")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.text-extraction.document-reference.bucket-name",
                    "custom-document-bucket-name")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.text-extraction.results-reference.connection",
                    "custom-results-connection")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.text-extraction.results-reference.bucket-name",
                    "custom-results-bucket-name")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.text-extraction.log-requests",
                    "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.text-extraction.log-responses",
                    "true")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(WatsonxTestUtil.class));

    @Inject
    TextExtractionService textExtraction;

    @Inject
    @ModelName("custom")
    TextExtractionService customTextExtraction;

    @Test
    void textExtractionPropertiesTest() {
        var textExtractionConfig = langchain4jWatsonConfig.defaultConfig().textExtraction().orElseThrow();
        assertEquals(false, textExtractionConfig.logRequests().orElse(false));
        assertEquals(false, textExtractionConfig.logResponses().orElse(false));
    }

    @Test
    void customTextExtractionPropertiesTest() {
        var textExtractionConfig = langchain4jWatsonConfig.namedConfig().get("custom").textExtraction().orElseThrow();
        assertEquals(true, textExtractionConfig.logRequests().orElse(false));
        assertEquals(true, textExtractionConfig.logResponses().orElse(false));
    }

    @Test
    void textExtractionTest() throws Exception {
        assertNotNull(textExtraction);
        assertNotNull(customTextExtraction);
    }
}
