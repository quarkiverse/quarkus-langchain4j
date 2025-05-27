package io.quarkiverse.langchain4j.watsonx.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.http.RequestMethod.DELETE;
import static com.github.tomakehurst.wiremock.http.RequestMethod.GET;
import static com.github.tomakehurst.wiremock.http.RequestMethod.POST;
import static com.github.tomakehurst.wiremock.http.RequestMethod.PUT;
import static io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.Type.MD;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_COS_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_TEXT_EXTRACTION_RESULT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_TEXT_EXTRACTION_START_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.VERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.Date;
import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.TextExtractionDataReference;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.TextExtractionParameters;
import io.quarkiverse.langchain4j.watsonx.runtime.TextExtraction;
import io.quarkiverse.langchain4j.watsonx.runtime.TextExtraction.Parameters;
import io.quarkus.test.QuarkusUnitTest;

public class TextExtractionInjectTest extends WireMockAbstract {

    static String PROCESS_EXTRACTION_ID = "custom-id";
    static String FILE_NAME = "test.pdf";
    static String OUTPUT_FILE_NAME = "test.md";

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.base-url", URL_COS_SERVER)
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
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.text-extraction.base-url", URL_COS_SERVER)
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
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(WireMockUtil.class)
                    .addAsResource(FILE_NAME));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType())
                .response(BEARER_TOKEN, new Date())
                .build();
    }

    @Inject
    TextExtraction textExtraction;

    @Inject
    @ModelName("custom")
    TextExtraction customTextExtraction;

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

        File file = new File(TextExtractionInjectTest.class.getClassLoader().getResource(FILE_NAME).toURI());

        var documentReference = TextExtractionDataReference.of("document-connection", FILE_NAME,
                "document-bucket-name");
        var resultsReference = TextExtractionDataReference.of("results-connection", OUTPUT_FILE_NAME,
                "results-bucket-name");

        var request = TextExtractionRequest.builder()
                .documentReference(documentReference)
                .resultsReference(resultsReference)
                .parameters(new TextExtractionParameters(List.of(MD), null, null, null, null, null, null))
                .projectId(PROJECT_ID)
                .spaceId(null)
                .build();

        var options = Parameters.builder()
                .removeOutputFile(true)
                .removeUploadedFile(true)
                .build();

        mockServers(request, "document-bucket-name", "results-bucket-name", true, true);

        var extractedText = textExtraction.uploadExtractAndFetch(file, options);
        assertEquals("Hello", extractedText);
        Thread.sleep(200); // Wait for the async calls.
        watsonxServer.verify(1, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(1, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
        cosServer.verify(1, putRequestedFor(urlEqualTo("/%s/%s".formatted("document-bucket-name", FILE_NAME))));
        cosServer.verify(1, getRequestedFor(urlEqualTo("/%s/%s".formatted("results-bucket-name", OUTPUT_FILE_NAME))));
        cosServer.verify(1, deleteRequestedFor(urlEqualTo("/%s/%s".formatted("document-bucket-name", FILE_NAME))));
        cosServer.verify(1, deleteRequestedFor(urlEqualTo("/%s/%s".formatted("results-bucket-name", OUTPUT_FILE_NAME))));
    }

    @Test
    void customTextExtractionTest() throws Exception {

        File file = new File(TextExtractionInjectTest.class.getClassLoader().getResource(FILE_NAME).toURI());

        var documentReference = TextExtractionDataReference.of("custom-document-connection", FILE_NAME,
                "custom-document-bucket-name");
        var resultsReference = TextExtractionDataReference.of("custom-results-connection", OUTPUT_FILE_NAME,
                "custom-results-bucket-name");

        var request = TextExtractionRequest.builder()
                .documentReference(documentReference)
                .resultsReference(resultsReference)
                .parameters(new TextExtractionParameters(List.of(MD), null, null, null, null, null, null))
                .projectId(PROJECT_ID)
                .spaceId(null)
                .build();

        var options = Parameters.builder()
                .removeOutputFile(true)
                .removeUploadedFile(true)
                .build();

        mockServers(request, "custom-document-bucket-name", "custom-results-bucket-name", true, true);

        var extractedText = customTextExtraction.uploadExtractAndFetch(file, options);
        assertEquals("Hello", extractedText);
        Thread.sleep(200); // Wait for the async calls.
        watsonxServer.verify(1, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(1, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
        cosServer.verify(1, putRequestedFor(urlEqualTo("/%s/%s".formatted("custom-document-bucket-name", FILE_NAME))));
        cosServer.verify(1, getRequestedFor(urlEqualTo("/%s/%s".formatted("custom-results-bucket-name", OUTPUT_FILE_NAME))));
        cosServer.verify(1, deleteRequestedFor(urlEqualTo("/%s/%s".formatted("custom-document-bucket-name", FILE_NAME))));
        cosServer.verify(1, deleteRequestedFor(urlEqualTo("/%s/%s".formatted("custom-results-bucket-name", OUTPUT_FILE_NAME))));
    }

    private void mockServers(TextExtractionRequest body, String documentBucketName, String resultsBucketName,
            boolean deleteUploadedFile,
            boolean deleteOutputFile) {
        // Mock the upload local file operation.
        mockCosBuilder(PUT, documentBucketName, FILE_NAME, 200).build();

        // Mock extracted file result.
        mockCosBuilder(GET, resultsBucketName, OUTPUT_FILE_NAME, 200)
                .response("Hello")
                .build();

        if (deleteUploadedFile) {
            mockCosBuilder(DELETE, documentBucketName, FILE_NAME, 200).build();
        }

        if (deleteOutputFile) {
            mockCosBuilder(DELETE, resultsBucketName, OUTPUT_FILE_NAME, 200).build();
        }

        // Mock start extraction.
        mockTextExtractionBuilder(POST, URL_WATSONX_TEXT_EXTRACTION_START_API, 200)
                .body(body)
                .response(body, PROCESS_EXTRACTION_ID, "submitted")
                .build();

        // Mock result extraction.
        mockTextExtractionBuilder(GET,
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID, VERSION), 200)
                .response(body, PROCESS_EXTRACTION_ID, "completed")
                .build();
    }
}
