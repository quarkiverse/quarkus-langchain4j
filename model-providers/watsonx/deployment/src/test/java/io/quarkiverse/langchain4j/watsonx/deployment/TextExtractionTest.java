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
import static com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionParameters.Type.HTML;
import static com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionParameters.Type.JSON;
import static com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionParameters.Type.MD;
import static com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionParameters.Type.PAGE_IMAGES;
import static com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionParameters.Type.PLAIN_TEXT;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_COS_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_TEXT_EXTRACTION_RESULT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_TEXT_EXTRACTION_START_API;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.core.exception.model.WatsonxError;
import com.ibm.watsonx.ai.textprocessing.CosReference;
import com.ibm.watsonx.ai.textprocessing.Language;
import com.ibm.watsonx.ai.textprocessing.OcrMode;
import com.ibm.watsonx.ai.textprocessing.textextraction.Parameters;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionException;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionParameters;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionParameters.EmbeddedImageMode;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionParameters.Mode;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionRequest;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionResponse;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionService;

import io.quarkus.test.QuarkusUnitTest;

public class TextExtractionTest extends WireMockAbstract {

    static String CONNECTION_ID = "my-connection-id";
    static String BUCKET_NAME = "my-bucket-name";
    static String FILE_NAME = "test.pdf";
    static String PROCESS_EXTRACTION_ID = "my-id";

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.cos-url", URL_COS_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.document-reference.connection",
                    CONNECTION_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.document-reference.bucket-name",
                    BUCKET_NAME)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.results-reference.connection",
                    CONNECTION_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.results-reference.bucket-name",
                    BUCKET_NAME)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(WireMockUtil.class)
                    .addAsResource(FILE_NAME));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType().orElse(null))
                .response(BEARER_TOKEN, new Date())
                .build();
    }

    @Inject
    TextExtractionService textExtraction;

    @Test
    void extractAndFetchTest() throws Exception {

        String fileAlreadyUploaded = "test.pdf";
        String outputFileName = fileAlreadyUploaded.replace(".pdf", ".md");

        TextExtractionRequest body = createDefaultRequest();

        mockServers(body, outputFileName, false, false);

        String textExtracted = textExtraction.extractAndFetch(fileAlreadyUploaded);
        assertEquals("Hello", textExtracted);

        textExtracted = textExtraction.extractAndFetch(fileAlreadyUploaded);
        assertEquals("Hello", textExtracted);

        watsonxServer.verify(2, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(2, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
        cosServer.verify(0, putRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(2, getRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
    }

    @Test
    void uploadExtractAndFetchInputStreamTest() throws Exception {

        String fileName = "test.pdf";
        InputStream inputStream = TextExtractionTest.class.getClassLoader().getResourceAsStream(FILE_NAME);
        String outputFileName = fileName.replace(".pdf", ".md");

        TextExtractionRequest body = createDefaultRequest();

        mockServers(body, outputFileName, false, false);

        String textExtracted = textExtraction.uploadExtractAndFetch(inputStream, fileName);
        assertEquals("Hello", textExtracted);

        textExtracted = textExtraction.uploadExtractAndFetch(inputStream, fileName);
        assertEquals("Hello", textExtracted);

        watsonxServer.verify(2, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(2, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
        cosServer.verify(2, putRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(2, getRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
    }

    @Test
    void uploadExtractAndFetchTest() throws Exception {

        File file = new File(TextExtractionTest.class.getClassLoader().getResource(FILE_NAME).toURI());
        String outputFileName = file.getName().replace(".pdf", ".md");

        TextExtractionRequest body = createDefaultRequest();

        mockServers(body, outputFileName, false, false);

        String textExtracted = textExtraction.uploadExtractAndFetch(file);
        assertEquals("Hello", textExtracted);

        textExtracted = textExtraction.uploadExtractAndFetch(file);
        assertEquals("Hello", textExtracted);

        watsonxServer.verify(2, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(2, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
        cosServer.verify(2, putRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(2, getRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
    }

    @Test
    void uploadExtractAndFetchFileNotFoundTest() throws Exception {
        File file = new File("doesnotexist.pdf");
        String outputFileName = file.getName().replace(".pdf", ".md");

        TextExtractionRequest body = createDefaultRequest();

        mockServers(body, outputFileName, false, false);

        assertThrows(FileNotFoundException.class,
                () -> textExtraction.uploadExtractAndFetch(file));

        watsonxServer.verify(0, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(0, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
        cosServer.verify(0, putRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, getRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
    }

    @Test
    void startExtractionTest() throws Exception {

        String fileAlreadyUploaded = "test.pdf";
        String outputFileName = fileAlreadyUploaded.replace(".pdf", ".md");

        TextExtractionRequest body = createDefaultRequest();

        mockServers(body, outputFileName, false, false);

        String processId = textExtraction.startExtraction(fileAlreadyUploaded).metadata().id();
        assertEquals(PROCESS_EXTRACTION_ID, processId);

        processId = textExtraction.startExtraction(fileAlreadyUploaded).metadata().id();
        assertEquals(PROCESS_EXTRACTION_ID, processId);

        watsonxServer.verify(2, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(0, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
        cosServer.verify(0, putRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, getRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
    }

    @Test
    void uploadAndStartExtractionTest() throws Exception {

        File file = new File(TextExtractionTest.class.getClassLoader().getResource(FILE_NAME).toURI());
        String outputFileName = file.getName().replace(".pdf", ".md");

        TextExtractionRequest body = createDefaultRequest();

        mockServers(body, outputFileName, false, false);

        String processId = textExtraction.uploadAndStartExtraction(file).metadata().id();
        assertEquals(PROCESS_EXTRACTION_ID, processId);

        processId = textExtraction.uploadAndStartExtraction(file).metadata().id();
        assertEquals(PROCESS_EXTRACTION_ID, processId);

        watsonxServer.verify(2, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(0, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
        cosServer.verify(2, putRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, getRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
    }

    @Test
    void uploadAndStartExtractionFileNotFoundTest() throws Exception {

        File file = new File("doesnotexist.pdf");
        String outputFileName = file.getName().replace(".pdf", ".md");

        TextExtractionRequest body = createDefaultRequest();

        mockServers(body, outputFileName, false, false);

        TextExtractionException ex = assertThrows(TextExtractionException.class,
                () -> textExtraction.uploadAndStartExtraction(file));
        assertEquals(ex.code(), "file_not_found");
        assertTrue(ex.getCause() instanceof FileNotFoundException);

        watsonxServer.verify(0, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(0, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
        cosServer.verify(0, putRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, getRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
    }

    @Test
    void uploadAndStartExtractionInputStreamTest() throws Exception {

        String fileName = "test.pdf";
        InputStream inputStream = TextExtractionTest.class.getClassLoader().getResourceAsStream(FILE_NAME);
        String outputFileName = fileName.replace(".pdf", ".md");

        TextExtractionRequest body = createDefaultRequest();

        mockServers(body, outputFileName, false, false);

        String processId = textExtraction.uploadAndStartExtraction(inputStream, fileName).metadata().id();
        assertEquals(PROCESS_EXTRACTION_ID, processId);

        processId = textExtraction.uploadAndStartExtraction(inputStream, fileName).metadata().id();
        assertEquals(PROCESS_EXTRACTION_ID, processId);

        watsonxServer.verify(2, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(0, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
        cosServer.verify(2, putRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, getRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
    }

    @Test
    void forceRemoveOutputAndUploadedFiles() throws Exception {

        watsonxServer.resetAll();
        cosServer.resetAll();

        String outputFileName = "myNewOutput.json";
        File file = new File(TextExtractionTest.class.getClassLoader().getResource(FILE_NAME).toURI());

        TextExtractionRequest body = new TextExtractionRequest(
                PROJECT_ID,
                null,
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(FILE_NAME),
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(outputFileName),
                Parameters.of(List.of(JSON.value())), null);

        mockServers(body, outputFileName, true, true);

        TextExtractionParameters options = TextExtractionParameters.builder()
                .outputFileName(outputFileName)
                .removeOutputFile(true)
                .removeUploadedFile(true)
                .requestedOutputs(JSON)
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> textExtraction.startExtraction(FILE_NAME, options),
                "The asynchronous version of startExtraction doesn't allow the use of the \"removeOutputFile\" and \"removeUploadedFile\" options");

        assertThrows(
                IllegalArgumentException.class,
                () -> textExtraction.uploadAndStartExtraction(file, options),
                "The asynchronous version of startExtraction doesn't allow the use of the \"removeOutputFile\" and \"removeUploadedFile\" options");

        watsonxServer.verify(0, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(0, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
        cosServer.verify(0, putRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, getRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));

        String extractedText = textExtraction.extractAndFetch(FILE_NAME, options);
        assertEquals("Hello", extractedText);
        Thread.sleep(200); // Wait for the async calls.
        watsonxServer.verify(1, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(1, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
        cosServer.verify(0, putRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(1, getRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
        cosServer.verify(1, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(1, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));

        watsonxServer.resetAll();
        cosServer.resetAll();

        mockServers(body, outputFileName, true, true);

        extractedText = textExtraction.uploadExtractAndFetch(file, options);
        assertEquals("Hello", extractedText);
        Thread.sleep(200); // Wait for the async calls.
        watsonxServer.verify(1, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(1, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
        cosServer.verify(1, putRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(1, getRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
        cosServer.verify(1, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(1, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
    }

    @Test
    void mdOutputFileNameTest() throws Exception {
        String outputFileName = "test.md";

        TextExtractionRequest body = new TextExtractionRequest(
                PROJECT_ID,
                null,
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(FILE_NAME),
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(outputFileName),
                Parameters.of(List.of(MD.value())), null);

        mockServers(body, outputFileName, false, false);

        textExtraction.startExtraction(FILE_NAME);
        watsonxServer.verify(1, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(0, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
        cosServer.verify(0, putRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, getRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
    }

    @Test
    void jsonOutputFileNameTest() throws Exception {
        String outputFileName = "test.json";

        TextExtractionRequest body = new TextExtractionRequest(
                PROJECT_ID,
                null,
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(FILE_NAME),
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(outputFileName),
                Parameters.of(List.of(JSON.value())), null);

        mockServers(body, outputFileName, false, false);

        textExtraction.startExtraction(FILE_NAME, TextExtractionParameters.builder().requestedOutputs(JSON).build());
        watsonxServer.verify(1, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(0, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
        cosServer.verify(0, putRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, getRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
    }

    @Test
    void textPlainOutputFileNameTest() throws Exception {
        String outputFileName = "test.txt";

        TextExtractionRequest body = new TextExtractionRequest(
                PROJECT_ID,
                null,
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(FILE_NAME),
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(outputFileName),
                Parameters.of(List.of(PLAIN_TEXT.value())), null);

        mockServers(body, outputFileName, false, false);

        textExtraction.startExtraction(FILE_NAME, TextExtractionParameters.builder().requestedOutputs(PLAIN_TEXT).build());
        watsonxServer.verify(1, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(0, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
        cosServer.verify(0, putRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, getRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
    }

    @Test
    void pageImagesOutputFolderTest() throws Exception {
        String outputFolderName = "/";

        TextExtractionRequest body = new TextExtractionRequest(
                PROJECT_ID,
                null,
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(FILE_NAME),
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(outputFolderName),
                Parameters.of(List.of(PAGE_IMAGES.value())), null);

        mockServers(body, outputFolderName, false, false);

        textExtraction.startExtraction(FILE_NAME, TextExtractionParameters.builder().requestedOutputs(PAGE_IMAGES).build());
        watsonxServer.verify(1, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(0, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
        cosServer.verify(0, putRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, getRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFolderName))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFolderName))));
    }

    @Test
    void multipleTypeOutputFolderTest() throws Exception {
        String outputFolderName = "/";

        TextExtractionRequest body = new TextExtractionRequest(
                PROJECT_ID,
                null,
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(FILE_NAME),
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(outputFolderName),
                Parameters.of(List.of(MD.value(), JSON.value(), PLAIN_TEXT.value(), PAGE_IMAGES.value(), HTML.value())), null);

        mockServers(body, outputFolderName, false, false);

        textExtraction.startExtraction(FILE_NAME,
                TextExtractionParameters.builder().requestedOutputs(MD, JSON, PLAIN_TEXT, PAGE_IMAGES, HTML).build());
        watsonxServer.verify(1, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(0, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
        cosServer.verify(0, putRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, getRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFolderName))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFolderName))));
    }

    @Test
    void overrideParametersTest() throws Exception {
        String outputFolderName = "/";

        TextExtractionRequest body = new TextExtractionRequest(
                PROJECT_ID,
                null,
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(FILE_NAME),
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(outputFolderName),
                new Parameters(
                        List.of(MD.value(), JSON.value(), PLAIN_TEXT.value(), PAGE_IMAGES.value(), HTML.value()),
                        Mode.STANDARD.value(),
                        OcrMode.DISABLED.value(), null, false, "disabled", 16, null, null, null),
                null);

        mockServers(body, outputFolderName, false, false);

        textExtraction.startExtraction(
                FILE_NAME,
                TextExtractionParameters.builder()
                        .requestedOutputs(MD, JSON, PLAIN_TEXT, PAGE_IMAGES, HTML)
                        .mode(Mode.STANDARD)
                        .ocrMode(OcrMode.DISABLED)
                        .autoRotationCorrection(false)
                        .createEmbeddedImages(EmbeddedImageMode.DISABLED)
                        .outputDpi(16)
                        .build());

        body = new TextExtractionRequest(
                PROJECT_ID,
                null,
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(FILE_NAME),
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(outputFolderName),
                new Parameters(
                        List.of(MD.value(), JSON.value(), PLAIN_TEXT.value(), PAGE_IMAGES.value(), HTML.value()),
                        Mode.HIGH_QUALITY.value(),
                        OcrMode.ENABLED.value(), List.of(Language.ITALIAN.isoCode()), true,
                        EmbeddedImageMode.ENABLED_PLACEHOLDER.value(), 32, null, null,
                        null),
                null);

        mockServers(body, outputFolderName, false, false);

        textExtraction.startExtraction(
                FILE_NAME,
                TextExtractionParameters.builder()
                        .requestedOutputs(MD, JSON, PLAIN_TEXT, PAGE_IMAGES, HTML)
                        .mode(Mode.HIGH_QUALITY)
                        .ocrMode(OcrMode.ENABLED)
                        .autoRotationCorrection(true)
                        .createEmbeddedImages(EmbeddedImageMode.ENABLED_PLACEHOLDER)
                        .languages(Language.ITALIAN)
                        .outputDpi(32)
                        .build());

        watsonxServer.verify(2, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
    }

    @Test
    void fetchWithMultipleTypes() throws URISyntaxException {

        String outputFolderName = "myFolder/";

        var paremeters = TextExtractionParameters.builder()
                .outputFileName("myFolder/")
                .requestedOutputs(MD, JSON, PLAIN_TEXT, PAGE_IMAGES, HTML)
                .mode(Mode.STANDARD)
                .ocrMode(OcrMode.DISABLED)
                .autoRotationCorrection(false)
                .createEmbeddedImages(EmbeddedImageMode.DISABLED)
                .outputDpi(16)
                .build();

        TextExtractionRequest body = new TextExtractionRequest(
                PROJECT_ID,
                null,
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(FILE_NAME),
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(outputFolderName),
                new Parameters(
                        List.of(MD.value(), JSON.value(), PLAIN_TEXT.value(), PAGE_IMAGES.value(), HTML.value()),
                        Mode.STANDARD.value(),
                        OcrMode.DISABLED.value(), null, false, "disabled", 16, false, null, null),
                null);

        mockServers(body, outputFolderName, false, false);
        var ex = assertThrows(TextExtractionException.class, () -> {
            textExtraction.extractAndFetch(
                    FILE_NAME,
                    paremeters);
        });
        assertEquals("fetch_operation_not_allowed", ex.code());
        assertEquals("The fetch operation cannot be executed if more than one file is to be generated",
                ex.getMessage());

        File file = new File(TextExtractionTest.class.getClassLoader().getResource(FILE_NAME).toURI());

        ex = assertThrows(TextExtractionException.class, () -> {
            textExtraction.uploadExtractAndFetch(
                    file,
                    TextExtractionParameters.builder()
                            .outputFileName("myFolder/")
                            .requestedOutputs(MD, JSON, PLAIN_TEXT, PAGE_IMAGES, HTML)
                            .mode(Mode.STANDARD)
                            .ocrMode(OcrMode.DISABLED)
                            .autoRotationCorrection(false)
                            .createEmbeddedImages(EmbeddedImageMode.DISABLED)
                            .outputDpi(16)
                            .build());
        });
        assertEquals("fetch_operation_not_allowed", ex.code());
        assertEquals("The fetch operation cannot be executed if more than one file is to be generated",
                ex.getMessage());

        InputStream inputStream = TextExtractionTest.class.getClassLoader().getResourceAsStream(FILE_NAME);

        ex = assertThrows(TextExtractionException.class, () -> {
            textExtraction.uploadExtractAndFetch(
                    inputStream,
                    FILE_NAME,
                    paremeters);
        });
        assertEquals("fetch_operation_not_allowed", ex.code());
        assertEquals("The fetch operation cannot be executed if more than one file is to be generated",
                ex.getMessage());
    }

    @Test
    void fetchPageImages() throws URISyntaxException {
        String outputFolderName = "myFolder/";
        InputStream inputStream = TextExtractionTest.class.getClassLoader().getResourceAsStream(FILE_NAME);
        File file = new File(TextExtractionTest.class.getClassLoader().getResource(FILE_NAME).toURI());

        TextExtractionRequest body = new TextExtractionRequest(
                PROJECT_ID,
                null,
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(FILE_NAME),
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(outputFolderName),
                Parameters.of(List.of(PAGE_IMAGES.value())),
                null);

        TextExtractionParameters parameters = TextExtractionParameters.builder()
                .outputFileName("myFolder/")
                .requestedOutputs(PAGE_IMAGES)
                .build();

        mockServers(body, outputFolderName, false, false);
        var ex = assertThrows(TextExtractionException.class, () -> {
            textExtraction.extractAndFetch(
                    FILE_NAME,
                    parameters);
        });
        assertEquals("fetch_operation_not_allowed", ex.code());
        assertEquals("The fetch operation cannot be executed for the type \"page_images\"",
                ex.getMessage());

        ex = assertThrows(TextExtractionException.class, () -> {
            textExtraction.uploadExtractAndFetch(
                    file,
                    parameters);
        });
        assertEquals("fetch_operation_not_allowed", ex.code());
        assertEquals("The fetch operation cannot be executed for the type \"page_images\"",
                ex.getMessage());

        ex = assertThrows(TextExtractionException.class, () -> {
            textExtraction.uploadExtractAndFetch(
                    inputStream,
                    FILE_NAME,
                    parameters);
        });
        assertEquals("fetch_operation_not_allowed", ex.code());
        assertEquals("The fetch operation cannot be executed for the type \"page_images\"",
                ex.getMessage());
    }

    @Test
    void simulateLongResponseTest() throws Exception {

        String outputFileName = FILE_NAME.replace(".pdf", ".md");

        TextExtractionRequest body = createDefaultRequest();

        mockTextExtractionBuilder(POST, URL_WATSONX_TEXT_EXTRACTION_START_API, 200)
                .body(body)
                .response(body, PROCESS_EXTRACTION_ID, "submitted")
                .scenario(Scenario.STARTED, "firstIteration")
                .build();

        mockTextExtractionBuilder(GET,
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID), 200)
                .response(body, PROCESS_EXTRACTION_ID, "running")
                .scenario("firstIteration", "secondIteration")
                .build();

        mockTextExtractionBuilder(GET,
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID), 200)
                .response(body, PROCESS_EXTRACTION_ID, "completed")
                .scenario("secondIteration", Scenario.STARTED)
                .build();

        mockCosBuilder(GET, BUCKET_NAME, outputFileName, 200)
                .response("Hello")
                .build();

        String extractedValue = textExtraction.extractAndFetch(FILE_NAME);
        assertEquals("Hello", extractedValue);
        watsonxServer.verify(1, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(2, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
    }

    @Test
    void simulateTimeoutResponseTest() {

        String outputFileName = FILE_NAME.replace(".pdf", ".md");

        TextExtractionRequest body = createDefaultRequest();

        mockTextExtractionBuilder(POST, URL_WATSONX_TEXT_EXTRACTION_START_API, 200)
                .body(body)
                .response(body, PROCESS_EXTRACTION_ID, "submitted")
                .scenario(Scenario.STARTED, "firstIteration")
                .build();

        mockTextExtractionBuilder(GET,
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID), 200)
                .response(body, PROCESS_EXTRACTION_ID, "running")
                .scenario("firstIteration", "secondIteration")
                .build();

        mockTextExtractionBuilder(GET,
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID), 200)
                .response(body, PROCESS_EXTRACTION_ID, "completed")
                .scenario("secondIteration", Scenario.STARTED)
                .build();

        mockCosBuilder(GET, BUCKET_NAME, outputFileName, 200)
                .response("Hello")
                .build();

        TextExtractionParameters options = TextExtractionParameters.builder()
                .timeout(Duration.ofMillis(100))
                .build();

        var ex = assertThrows(
                TextExtractionException.class,
                () -> textExtraction.extractAndFetch(FILE_NAME, options));

        assertEquals("Execution to extract test.pdf file took longer than the timeout set by 100 milliseconds",
                ex.getMessage());

        watsonxServer.verify(1, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(1, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
    }

    @Test
    void simulateFailedStatusOnResponseTest() throws Exception {

        String outputFileName = FILE_NAME.replace(".pdf", ".md");
        File file = new File(TextExtractionTest.class.getClassLoader().getResource(FILE_NAME).toURI());

        TextExtractionRequest request = createDefaultRequest();

        TextExtractionParameters parameters = TextExtractionParameters.builder()
                .removeOutputFile(true)
                .removeUploadedFile(true)
                .build();

        mockCosBuilder(PUT, BUCKET_NAME, FILE_NAME, 200).build();
        mockCosBuilder(DELETE, BUCKET_NAME, FILE_NAME, 200).build();

        mockTextExtractionBuilder(POST, URL_WATSONX_TEXT_EXTRACTION_START_API, 200)
                .body(request)
                .response(request, PROCESS_EXTRACTION_ID, "submitted")
                .build();

        mockTextExtractionBuilder(GET,
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID), 200)
                .response(request, PROCESS_EXTRACTION_ID, "running")
                .scenario(Scenario.STARTED, "failed")
                .build();

        mockTextExtractionBuilder(GET,
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID), 200)
                .failResponse(request, PROCESS_EXTRACTION_ID)
                .scenario("failed", Scenario.STARTED)
                .build();

        var ex = assertThrows(
                TextExtractionException.class,
                () -> textExtraction.extractAndFetch(FILE_NAME, parameters));

        assertEquals(ex.code(), "file_download_error");
        assertEquals(ex.getMessage(), "error message");

        ex = assertThrows(
                TextExtractionException.class,
                () -> textExtraction.uploadExtractAndFetch(file, parameters));

        assertEquals(ex.code(), "file_download_error");
        assertEquals(ex.getMessage(), "error message");

        Thread.sleep(200); // Wait for the async calls.
        watsonxServer.verify(2, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(4, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
        cosServer.verify(1, putRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, getRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
        cosServer.verify(2, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
    }

    @Test
    void noSuchBucketTest() throws Exception {

        String outputFileName = FILE_NAME.replace(".pdf", ".md");
        File file = new File(TextExtractionTest.class.getClassLoader().getResource(FILE_NAME).toURI());

        mockCosBuilder(PUT, BUCKET_NAME, FILE_NAME, 404)
                .response("""
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Error>
                            <Code>NoSuchBucket</Code>
                            <Message>The specified bucket does not exist.</Message>
                            <Resource>/my-bucket-name/test.pdf</Resource>
                            <RequestId>my-request-id</RequestId>
                            <httpStatusCode>404</httpStatusCode>
                        </Error>
                        """.trim())
                .build();

        assertThrows(
                WatsonxException.class,
                () -> textExtraction.uploadAndStartExtraction(file),
                "The specified bucket does not exist.");

        assertThrows(
                WatsonxException.class,
                () -> textExtraction.uploadExtractAndFetch(file),
                "The specified bucket does not exist.");

        watsonxServer.verify(0, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(0, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
        cosServer.verify(2, putRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, getRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
    }

    @Test
    void noSuchKeyTest() throws Exception {

        String outputFileName = FILE_NAME.replace(".pdf", ".md");
        File file = new File(TextExtractionTest.class.getClassLoader().getResource(FILE_NAME).toURI());

        TextExtractionRequest body = createDefaultRequest();

        mockCosBuilder(PUT, BUCKET_NAME, FILE_NAME, 200).build();
        mockCosBuilder(GET, BUCKET_NAME, outputFileName, 404)
                .response("""
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Error>
                            <Code>NoSuchKey</Code>
                            <Message>The specified key does not exist.</Message>
                            <Resource>/my-bucket-name/test.pdf</Resource>
                            <RequestId>my-request-id</RequestId>
                            <httpStatusCode>404</httpStatusCode>
                        </Error>
                        """.trim())
                .build();

        mockTextExtractionBuilder(POST, URL_WATSONX_TEXT_EXTRACTION_START_API, 200)
                .body(body)
                .response(body, PROCESS_EXTRACTION_ID, "submitted")
                .build();

        mockTextExtractionBuilder(GET,
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID), 200)
                .response(body, PROCESS_EXTRACTION_ID, "completed")
                .build();

        assertThrows(
                WatsonxException.class,
                () -> textExtraction.extractAndFetch(FILE_NAME),
                "The specified key does not exist.");

        assertThrows(
                WatsonxException.class,
                () -> textExtraction.uploadExtractAndFetch(file),
                "The specified key does not exist.");

        watsonxServer.verify(2, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
        watsonxServer.verify(2, getRequestedFor(urlPathEqualTo("/ml/v1/text/extractions/" + PROCESS_EXTRACTION_ID)));
        cosServer.verify(1, putRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(2, getRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
        cosServer.verify(0, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, outputFileName))));
    }

    @Test
    void textExtractionEventDoesntExistTest() {

        mockTextExtractionBuilder(GET,
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID), 404)
                .response("""
                            {
                                "trace": "9ddfccd50f6649d9913810df36578d38",
                                "errors": [
                                    {
                                        "code": "text_extraction_event_does_not_exist",
                                        "message": "Text extraction request does not exist."
                                    }
                                ]
                            }
                        """)
                .build();

        var ex = assertThrows(WatsonxException.class,
                () -> textExtraction.fetchExtractionRequest(PROCESS_EXTRACTION_ID));
        assertTrue(ex.details().orElseThrow().errors().get(0).is(WatsonxError.Code.TEXT_EXTRACTION_EVENT_DOES_NOT_EXIST));
    }

    @Test
    void checkExtractionStatusTest() throws TextExtractionException {

        TextExtractionRequest request = createDefaultRequest();

        mockTextExtractionBuilder(GET,
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID), 200)
                .response(request, PROCESS_EXTRACTION_ID, "COMPLETED")
                .build();

        TextExtractionResponse response = textExtraction.fetchExtractionRequest(PROCESS_EXTRACTION_ID);
        assertEquals(request.documentReference(), response.entity().documentReference());
        assertEquals(request.resultsReference(), response.entity().resultsReference());
        assertEquals(PROCESS_EXTRACTION_ID, response.metadata().id());
        assertEquals(PROJECT_ID, response.metadata().projectId());
        assertNotNull(response.metadata().createdAt());
        assertEquals("COMPLETED", response.entity().results().status());
        assertNotNull(response.entity().results().completedAt());
        assertNull(response.entity().results().error());
        assertEquals(1, response.entity().results().numberPagesProcessed());
        assertNotNull(response.entity().results().runningAt());
    }

    @Test
    void overrideConnectionIdAndBucket() throws Exception {

        String outputFileName = FILE_NAME.replace(".pdf", ".md");
        File file = new File(TextExtractionTest.class.getClassLoader().getResource(FILE_NAME).toURI());

        String NEW_CONNECTION_ID = "my-new-connection-id";
        String NEW_BUCKET_NAME = "my-new-bucket";

        TextExtractionRequest request = new TextExtractionRequest(
                PROJECT_ID,
                null,
                CosReference.of(NEW_CONNECTION_ID, NEW_BUCKET_NAME).toDataReference(FILE_NAME),
                CosReference.of(NEW_CONNECTION_ID, NEW_BUCKET_NAME).toDataReference(outputFileName),
                Parameters.of(List.of(MD.value())),
                null);

        // Mock the upload local file operation.
        mockCosBuilder(PUT, NEW_BUCKET_NAME, FILE_NAME, 200).build();

        // Mock extracted file result.
        mockCosBuilder(GET, NEW_BUCKET_NAME, outputFileName, 200)
                .response("Hello")
                .build();

        // Mock start extraction.
        mockTextExtractionBuilder(POST, URL_WATSONX_TEXT_EXTRACTION_START_API, 200)
                .body(request)
                .response(request, PROCESS_EXTRACTION_ID, "submitted")
                .build();

        // Mock result extraction.
        mockTextExtractionBuilder(GET,
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID), 200)
                .response(request, PROCESS_EXTRACTION_ID, "completed")
                .build();

        TextExtractionParameters options = TextExtractionParameters.builder()
                .documentReference(CosReference.of(NEW_CONNECTION_ID, NEW_BUCKET_NAME))
                .outputFileName(outputFileName)
                .removeOutputFile(false)
                .removeUploadedFile(false)
                .resultReference(CosReference.of(NEW_CONNECTION_ID, NEW_BUCKET_NAME))
                .requestedOutputs(MD)
                .build();

        assertDoesNotThrow(() -> textExtraction.extractAndFetch(FILE_NAME, options));
        assertDoesNotThrow(() -> textExtraction.startExtraction(FILE_NAME, options));
        assertDoesNotThrow(() -> textExtraction.uploadAndStartExtraction(file, options));
        assertDoesNotThrow(() -> textExtraction.uploadExtractAndFetch(file, options));
    }

    @Test
    void deleteFileTest() {

        mockCosBuilder(DELETE, BUCKET_NAME, FILE_NAME, 200).build();
        assertDoesNotThrow(() -> textExtraction.deleteFile(BUCKET_NAME, FILE_NAME));

        mockCosBuilder(DELETE, BUCKET_NAME, FILE_NAME, 404)
                .response("""
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Error>
                        <Code>NoSuchBucket</Code>
                        <Message>The specified bucket does not exist.</Message>
                        <Resource>/andreaproject-donotdelete-pr-xnran4g4ptd1wos/ciao.pdf</Resource>
                        <RequestId>27482371-17d6-47c7-a526-5a655074bed2</RequestId>
                        <httpStatusCode>404</httpStatusCode>
                        </Error>""".trim()).build();
        assertThrows(WatsonxException.class, () -> textExtraction.deleteFile(BUCKET_NAME, FILE_NAME));

        // Return an expired token.
        mockIAMBuilder(200)
                .scenario(Scenario.STARTED, "retry")
                .response("expired_token", Date.from(Instant.now().minusSeconds(3)))
                .build();

        // Second call (retryOn) returns 200
        mockIAMBuilder(200)
                .scenario("retry", Scenario.STARTED)
                .response("my_super_token", Date.from(Instant.now().plusMillis(2000)))
                .build();

        mockCosBuilder(DELETE, BUCKET_NAME, FILE_NAME, 403)
                .response("""
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Error>
                            <Code>AccessDenied</Code>
                            <Message>Access Denied</Message>
                            <Resource>/andreaproject-donotdelete-pr-xnran4g4ptd1wo/ciao.pdf</Resource>
                            <RequestId>df887c2b-43c3-4933-a3a1-b0e19e7c2231</RequestId>
                            <httpStatusCode>403</httpStatusCode>
                        </Error>""".trim())
                .token("expired_token")
                .scenario(Scenario.STARTED, "retry")
                .build();

        mockCosBuilder(DELETE, BUCKET_NAME, FILE_NAME, 200)
                .scenario("retry", Scenario.STARTED)
                .build();

        assertDoesNotThrow(() -> textExtraction.deleteFile(BUCKET_NAME, FILE_NAME));
    }

    private void mockServers(TextExtractionRequest body, String outputFileName, boolean deleteUploadedFile,
            boolean deleteOutputFile) {
        // Mock the upload local file operation.
        mockCosBuilder(PUT, BUCKET_NAME, FILE_NAME, 200).build();

        // Mock extracted file result.
        mockCosBuilder(GET, BUCKET_NAME, outputFileName, 200)
                .response("Hello")
                .build();

        if (deleteUploadedFile) {
            // Mock delete uploaded file.
            mockCosBuilder(DELETE, BUCKET_NAME, FILE_NAME, 200).build();
        }

        if (deleteOutputFile) {
            // Mock delete extracted file.
            mockCosBuilder(DELETE, BUCKET_NAME, outputFileName, 200).build();
        }

        // Mock start extraction.
        mockTextExtractionBuilder(POST, URL_WATSONX_TEXT_EXTRACTION_START_API, 200)
                .body(body)
                .response(body, PROCESS_EXTRACTION_ID, "submitted")
                .build();

        // Mock result extraction.
        mockTextExtractionBuilder(GET,
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID), 200)
                .response(body, PROCESS_EXTRACTION_ID, "completed")
                .build();
    }

    private TextExtractionRequest createDefaultRequest() {
        return new TextExtractionRequest(
                PROJECT_ID,
                null,
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(FILE_NAME),
                CosReference.of(CONNECTION_ID, BUCKET_NAME).toDataReference(FILE_NAME.replace(".pdf", ".md")),
                new Parameters(
                        List.of(MD.value()),
                        null, null, null, null, null, null, null, null, null),
                null);

    }
}
