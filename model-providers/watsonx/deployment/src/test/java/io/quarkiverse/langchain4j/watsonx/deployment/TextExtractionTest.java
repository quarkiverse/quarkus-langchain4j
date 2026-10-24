package io.quarkiverse.langchain4j.watsonx.deployment;

import static com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionParameters.Type.HTML;
import static com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionParameters.Type.JSON;
import static com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionParameters.Type.MD;
import static com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionParameters.Type.PAGE_IMAGES;
import static com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionParameters.Type.PLAIN_TEXT;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.COS_PATH;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_IAM_GENERATE_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_TEXT_EXTRACTION_RESULT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_TEXT_EXTRACTION_START_API;
import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.core.exception.model.WatsonxError;
import com.ibm.watsonx.ai.textprocessing.CosReference;
import com.ibm.watsonx.ai.textprocessing.Language;
import com.ibm.watsonx.ai.textprocessing.Mode;
import com.ibm.watsonx.ai.textprocessing.OcrMode;
import com.ibm.watsonx.ai.textprocessing.textextraction.Parameters;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionException;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionParameters;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionParameters.EmbeddedImageMode;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionRequest;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionResponse;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionService;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkus.test.QuarkusExtensionTest;

public class TextExtractionTest extends WatsonxMockTestBase {

    static String CONNECTION_ID = "my-connection-id";
    static String BUCKET_NAME = "my-bucket-name";
    static String FILE_NAME = "test.pdf";
    static String PROCESS_EXTRACTION_ID = "my-id";

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            // Cloud Object Storage has its own host, which the mock does not take over
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.cos-url",
                    "${quarkus.langchain4j.mock-api.url}" + COS_PATH)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.document-reference.connection",
                    CONNECTION_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.document-reference.bucket-name",
                    BUCKET_NAME)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.results-reference.connection",
                    CONNECTION_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.results-reference.bucket-name",
                    BUCKET_NAME)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(WatsonxTestUtil.class)
                    .addAsResource(FILE_NAME));

    /**
     * The extraction requests the stubs expect, see {@link #mockServers}.
     */
    List<TextExtractionRequest> expectedRequests = new ArrayList<>();

    /**
     * A token that expires at once, so that every test requests its own, as the token refresh of
     * {@link #deleteFileTest()} needs.
     */
    @BeforeEach
    void mockIamToken() {
        expectedRequests.clear();
        mock.raw().on("POST", URL_IAM_GENERATE_TOKEN).replies(iamToken(BEARER_TOKEN, new Date()));
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

        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).hasSize(2);
        assertThat(requests("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))).hasSize(2);
        assertThat(requests("PUT", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("GET", cosFile(outputFileName))).hasSize(2);
        assertThat(requests("DELETE", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("DELETE", cosFile(outputFileName))).isEmpty();
        assertStubbedRequests();
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

        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).hasSize(2);
        assertThat(requests("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))).hasSize(2);
        assertThat(requests("PUT", cosFile(FILE_NAME))).hasSize(2);
        assertThat(requests("GET", cosFile(outputFileName))).hasSize(2);
        assertThat(requests("DELETE", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("DELETE", cosFile(outputFileName))).isEmpty();
        assertStubbedRequests();
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

        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).hasSize(2);
        assertThat(requests("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))).hasSize(2);
        assertThat(requests("PUT", cosFile(FILE_NAME))).hasSize(2);
        assertThat(requests("GET", cosFile(outputFileName))).hasSize(2);
        assertThat(requests("DELETE", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("DELETE", cosFile(outputFileName))).isEmpty();
        assertStubbedRequests();
    }

    @Test
    void uploadExtractAndFetchFileNotFoundTest() throws Exception {
        File file = new File("doesnotexist.pdf");
        String outputFileName = file.getName().replace(".pdf", ".md");

        TextExtractionRequest body = createDefaultRequest();

        mockServers(body, outputFileName, false, false);

        assertThrows(FileNotFoundException.class,
                () -> textExtraction.uploadExtractAndFetch(file));

        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).isEmpty();
        assertThat(requests("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))).isEmpty();
        assertThat(requests("PUT", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("GET", cosFile(outputFileName))).isEmpty();
        assertThat(requests("DELETE", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("DELETE", cosFile(outputFileName))).isEmpty();
        assertStubbedRequests();
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

        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).hasSize(2);
        assertThat(requests("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))).isEmpty();
        assertThat(requests("PUT", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("GET", cosFile(outputFileName))).isEmpty();
        assertThat(requests("DELETE", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("DELETE", cosFile(outputFileName))).isEmpty();
        assertStubbedRequests();
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

        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).hasSize(2);
        assertThat(requests("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))).isEmpty();
        assertThat(requests("PUT", cosFile(FILE_NAME))).hasSize(2);
        assertThat(requests("GET", cosFile(outputFileName))).isEmpty();
        assertThat(requests("DELETE", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("DELETE", cosFile(outputFileName))).isEmpty();
        assertStubbedRequests();
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

        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).isEmpty();
        assertThat(requests("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))).isEmpty();
        assertThat(requests("PUT", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("GET", cosFile(outputFileName))).isEmpty();
        assertThat(requests("DELETE", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("DELETE", cosFile(outputFileName))).isEmpty();
        assertStubbedRequests();
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

        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).hasSize(2);
        assertThat(requests("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))).isEmpty();
        assertThat(requests("PUT", cosFile(FILE_NAME))).hasSize(2);
        assertThat(requests("GET", cosFile(outputFileName))).isEmpty();
        assertThat(requests("DELETE", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("DELETE", cosFile(outputFileName))).isEmpty();
        assertStubbedRequests();
    }

    @Test
    void forceRemoveOutputAndUploadedFiles() throws Exception {

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

        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).isEmpty();
        assertThat(requests("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))).isEmpty();
        assertThat(requests("PUT", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("GET", cosFile(outputFileName))).isEmpty();
        assertThat(requests("DELETE", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("DELETE", cosFile(outputFileName))).isEmpty();

        String extractedText = textExtraction.extractAndFetch(FILE_NAME, options);
        assertEquals("Hello", extractedText);
        Thread.sleep(200); // Wait for the async calls.
        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).hasSize(1);
        assertThat(requests("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))).hasSize(1);
        assertThat(requests("PUT", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("GET", cosFile(outputFileName))).hasSize(1);
        assertThat(requests("DELETE", cosFile(FILE_NAME))).hasSize(1);
        assertThat(requests("DELETE", cosFile(outputFileName))).hasSize(1);

        assertStubbedRequests();

        mock.reset();
        mockIamToken();

        mockServers(body, outputFileName, true, true);

        extractedText = textExtraction.uploadExtractAndFetch(file, options);
        assertEquals("Hello", extractedText);
        Thread.sleep(200); // Wait for the async calls.
        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).hasSize(1);
        assertThat(requests("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))).hasSize(1);
        assertThat(requests("PUT", cosFile(FILE_NAME))).hasSize(1);
        assertThat(requests("GET", cosFile(outputFileName))).hasSize(1);
        assertThat(requests("DELETE", cosFile(FILE_NAME))).hasSize(1);
        assertThat(requests("DELETE", cosFile(outputFileName))).hasSize(1);
        assertStubbedRequests();
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
        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).hasSize(1);
        assertThat(requests("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))).isEmpty();
        assertThat(requests("PUT", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("GET", cosFile(outputFileName))).isEmpty();
        assertThat(requests("DELETE", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("DELETE", cosFile(outputFileName))).isEmpty();
        assertStubbedRequests();
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
        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).hasSize(1);
        assertThat(requests("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))).isEmpty();
        assertThat(requests("PUT", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("GET", cosFile(outputFileName))).isEmpty();
        assertThat(requests("DELETE", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("DELETE", cosFile(outputFileName))).isEmpty();
        assertStubbedRequests();
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
        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).hasSize(1);
        assertThat(requests("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))).isEmpty();
        assertThat(requests("PUT", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("GET", cosFile(outputFileName))).isEmpty();
        assertThat(requests("DELETE", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("DELETE", cosFile(outputFileName))).isEmpty();
        assertStubbedRequests();
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
        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).hasSize(1);
        assertThat(requests("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))).isEmpty();
        assertThat(requests("PUT", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("GET", cosFile(outputFolderName))).isEmpty();
        assertThat(requests("DELETE", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("DELETE", cosFile(outputFolderName))).isEmpty();
        assertStubbedRequests();
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
        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).hasSize(1);
        assertThat(requests("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))).isEmpty();
        assertThat(requests("PUT", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("GET", cosFile(outputFolderName))).isEmpty();
        assertThat(requests("DELETE", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("DELETE", cosFile(outputFolderName))).isEmpty();
        assertStubbedRequests();
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

        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).hasSize(2);
        assertStubbedRequests();
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
        assertStubbedRequests();
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
        assertStubbedRequests();
    }

    @Test
    void simulateLongResponseTest() throws Exception {

        String outputFileName = FILE_NAME.replace(".pdf", ".md");

        TextExtractionRequest body = createDefaultRequest();
        expectedRequests.add(body);

        mock.raw().on("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)
                .reply(200, job(body, PROCESS_EXTRACTION_ID, "submitted"));

        mock.raw().on("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID)).replies(
                RawResponse.json(200, job(body, PROCESS_EXTRACTION_ID, "running")),
                RawResponse.json(200, job(body, PROCESS_EXTRACTION_ID, "completed")));

        mock.raw().on("GET", cosFile(outputFileName)).replies(cosFileContent("Hello"));

        String extractedValue = textExtraction.extractAndFetch(FILE_NAME);
        assertEquals("Hello", extractedValue);
        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).hasSize(1);
        assertThat(requests("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))).hasSize(2);
        assertStubbedRequests();
    }

    @Test
    void simulateTimeoutResponseTest() {

        String outputFileName = FILE_NAME.replace(".pdf", ".md");

        TextExtractionRequest body = createDefaultRequest();
        expectedRequests.add(body);

        mock.raw().on("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)
                .reply(200, job(body, PROCESS_EXTRACTION_ID, "submitted"));

        mock.raw().on("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID)).replies(
                RawResponse.json(200, job(body, PROCESS_EXTRACTION_ID, "running")),
                RawResponse.json(200, job(body, PROCESS_EXTRACTION_ID, "completed")));

        mock.raw().on("GET", cosFile(outputFileName)).replies(cosFileContent("Hello"));

        mock.raw().on("DELETE", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))
                .replies(RawResponse.of(204, Map.of(), null));

        mock.raw().on("DELETE", cosFile(FILE_NAME)).replies(cosFileContent("Hello"));

        TextExtractionParameters options = TextExtractionParameters.builder()
                .timeout(Duration.ofMillis(100))
                .build();

        var ex = assertThrows(
                TextExtractionException.class,
                () -> textExtraction.extractAndFetch(FILE_NAME, options));

        assertEquals("Execution to extract test.pdf file took longer than the timeout set by 100 milliseconds",
                ex.getMessage());

        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).hasSize(1);
        assertThat(requests("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))).hasSize(1);
        assertStubbedRequests();
    }

    @Test
    void simulateFailedStatusOnResponseTest() throws Exception {

        String outputFileName = FILE_NAME.replace(".pdf", ".md");
        File file = new File(TextExtractionTest.class.getClassLoader().getResource(FILE_NAME).toURI());

        TextExtractionRequest request = createDefaultRequest();
        expectedRequests.add(request);

        TextExtractionParameters parameters = TextExtractionParameters.builder()
                .removeOutputFile(true)
                .removeUploadedFile(true)
                .build();

        mock.raw().on("PUT", cosFile(FILE_NAME)).replies(cosFileContent(null));
        mock.raw().on("DELETE", cosFile(FILE_NAME)).replies(cosFileContent(null));

        mock.raw().on("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)
                .reply(200, job(request, PROCESS_EXTRACTION_ID, "submitted"));

        // every extraction is running when first polled and has failed when polled again
        mock.raw().on("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID)).replies(
                RawResponse.json(200, job(request, PROCESS_EXTRACTION_ID, "running")),
                RawResponse.json(200, failedJob(request, PROCESS_EXTRACTION_ID)),
                RawResponse.json(200, job(request, PROCESS_EXTRACTION_ID, "running")),
                RawResponse.json(200, failedJob(request, PROCESS_EXTRACTION_ID)));

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
        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).hasSize(2);
        assertThat(requests("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))).hasSize(4);
        assertThat(requests("PUT", cosFile(FILE_NAME))).hasSize(1);
        assertThat(requests("GET", cosFile(outputFileName))).isEmpty();
        assertThat(requests("DELETE", cosFile(FILE_NAME))).hasSize(2);
        assertThat(requests("DELETE", cosFile(outputFileName))).isEmpty();
        assertStubbedRequests();
    }

    @Test
    void noSuchBucketTest() throws Exception {

        String outputFileName = FILE_NAME.replace(".pdf", ".md");
        File file = new File(TextExtractionTest.class.getClassLoader().getResource(FILE_NAME).toURI());

        mock.raw().on("PUT", cosFile(FILE_NAME)).replies(RawResponse.of(404, Map.of("Content-Type", "application/xml"), """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Error>
                    <Code>NoSuchBucket</Code>
                    <Message>The specified bucket does not exist.</Message>
                    <Resource>/my-bucket-name/test.pdf</Resource>
                    <RequestId>my-request-id</RequestId>
                    <httpStatusCode>404</httpStatusCode>
                </Error>
                """.trim()));

        assertThrows(
                WatsonxException.class,
                () -> textExtraction.uploadAndStartExtraction(file),
                "The specified bucket does not exist.");

        assertThrows(
                WatsonxException.class,
                () -> textExtraction.uploadExtractAndFetch(file),
                "The specified bucket does not exist.");

        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).isEmpty();
        assertThat(requests("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))).isEmpty();
        assertThat(requests("PUT", cosFile(FILE_NAME))).hasSize(2);
        assertThat(requests("GET", cosFile(outputFileName))).isEmpty();
        assertThat(requests("DELETE", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("DELETE", cosFile(outputFileName))).isEmpty();
        assertStubbedRequests();
    }

    @Test
    void noSuchKeyTest() throws Exception {

        String outputFileName = FILE_NAME.replace(".pdf", ".md");
        File file = new File(TextExtractionTest.class.getClassLoader().getResource(FILE_NAME).toURI());

        TextExtractionRequest body = createDefaultRequest();
        expectedRequests.add(body);

        mock.raw().on("PUT", cosFile(FILE_NAME)).replies(cosFileContent(null));
        mock.raw().on("GET", cosFile(outputFileName)).replies(RawResponse.of(404, Map.of("Content-Type", "application/xml"),
                """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Error>
                            <Code>NoSuchKey</Code>
                            <Message>The specified key does not exist.</Message>
                            <Resource>/my-bucket-name/test.pdf</Resource>
                            <RequestId>my-request-id</RequestId>
                            <httpStatusCode>404</httpStatusCode>
                        </Error>
                        """.trim()));

        mock.raw().on("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)
                .reply(200, job(body, PROCESS_EXTRACTION_ID, "submitted"));

        mock.raw().on("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))
                .reply(200, job(body, PROCESS_EXTRACTION_ID, "completed"));

        assertThrows(
                WatsonxException.class,
                () -> textExtraction.extractAndFetch(FILE_NAME),
                "The specified key does not exist.");

        assertThrows(
                WatsonxException.class,
                () -> textExtraction.uploadExtractAndFetch(file),
                "The specified key does not exist.");

        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).hasSize(2);
        assertThat(requests("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))).hasSize(2);
        assertThat(requests("PUT", cosFile(FILE_NAME))).hasSize(1);
        assertThat(requests("GET", cosFile(outputFileName))).hasSize(2);
        assertThat(requests("DELETE", cosFile(FILE_NAME))).isEmpty();
        assertThat(requests("DELETE", cosFile(outputFileName))).isEmpty();
        assertStubbedRequests();
    }

    @Test
    void textExtractionEventDoesntExistTest() {

        mock.raw().on("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID)).reply(404, """
                    {
                        "trace": "9ddfccd50f6649d9913810df36578d38",
                        "errors": [
                            {
                                "code": "text_extraction_event_does_not_exist",
                                "message": "Text extraction request does not exist."
                            }
                        ]
                    }
                """);

        var ex = assertThrows(WatsonxException.class,
                () -> textExtraction.fetchExtractionRequest(PROCESS_EXTRACTION_ID));
        assertTrue(ex.details().orElseThrow().errors().get(0).is(WatsonxError.Code.TEXT_EXTRACTION_EVENT_DOES_NOT_EXIST));
        assertThat(apiRequests()).hasSize(1);
        assertStubbedRequests();
    }

    @Test
    void checkExtractionStatusTest() throws TextExtractionException {

        TextExtractionRequest request = createDefaultRequest();

        mock.raw().on("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))
                .reply(200, job(request, PROCESS_EXTRACTION_ID, "COMPLETED"));

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
        assertThat(apiRequests()).hasSize(1);
        assertStubbedRequests();
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
        expectedRequests.add(request);

        // Mock the upload local file operation.
        mock.raw().on("PUT", COS_PATH + "/" + NEW_BUCKET_NAME + "/" + FILE_NAME).replies(cosFileContent(null));

        // Mock extracted file result.
        mock.raw().on("GET", COS_PATH + "/" + NEW_BUCKET_NAME + "/" + outputFileName).replies(cosFileContent("Hello"));

        // Mock start extraction.
        mock.raw().on("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)
                .reply(200, job(request, PROCESS_EXTRACTION_ID, "submitted"));

        // Mock result extraction.
        mock.raw().on("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))
                .reply(200, job(request, PROCESS_EXTRACTION_ID, "completed"));

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
        assertStubbedRequests();
    }

    @Test
    void deleteFileTest() {

        mock.raw().on("DELETE", cosFile(FILE_NAME)).replies(
                cosFileContent(null),
                RawResponse.of(404, Map.of("Content-Type", "application/xml"), """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Error>
                        <Code>NoSuchBucket</Code>
                        <Message>The specified bucket does not exist.</Message>
                        <Resource>/andreaproject-donotdelete-pr-xnran4g4ptd1wos/ciao.pdf</Resource>
                        <RequestId>27482371-17d6-47c7-a526-5a655074bed2</RequestId>
                        <httpStatusCode>404</httpStatusCode>
                        </Error>""".trim()));

        assertDoesNotThrow(() -> textExtraction.deleteFile(BUCKET_NAME, FILE_NAME));
        assertThrows(WatsonxException.class, () -> textExtraction.deleteFile(BUCKET_NAME, FILE_NAME));
        assertStubbedRequests();
        assertThat(requests("DELETE", cosFile(FILE_NAME))).hasSize(2);

        mock.reset();

        // Return an expired token, then (retryOn) a valid one.
        mock.raw().on("POST", URL_IAM_GENERATE_TOKEN).replies(
                iamToken("expired_token", Date.from(Instant.now().minusSeconds(3))),
                iamToken("my_super_token", Date.from(Instant.now().plusMillis(2000))));

        // The expired token is denied, the valid one accepted.
        mock.raw().on("DELETE", cosFile(FILE_NAME)).replies(
                RawResponse.of(403, Map.of("Content-Type", "application/xml"), """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Error>
                            <Code>AccessDenied</Code>
                            <Message>Access Denied</Message>
                            <Resource>/andreaproject-donotdelete-pr-xnran4g4ptd1wo/ciao.pdf</Resource>
                            <RequestId>df887c2b-43c3-4933-a3a1-b0e19e7c2231</RequestId>
                            <httpStatusCode>403</httpStatusCode>
                        </Error>""".trim()),
                cosFileContent(null));

        assertDoesNotThrow(() -> textExtraction.deleteFile(BUCKET_NAME, FILE_NAME));

        assertThat(iamRequests()).hasSize(2);
        List<CapturedRequest> deletes = requests("DELETE", cosFile(FILE_NAME));
        assertThat(deletes).hasSize(2);
        assertThat(deletes.get(0).header("Authorization")).isEqualTo("Bearer expired_token");
        assertThat(deletes.get(1).header("Authorization")).isEqualTo("Bearer my_super_token");
    }

    private void mockServers(TextExtractionRequest body, String outputFileName, boolean deleteUploadedFile,
            boolean deleteOutputFile) {
        expectedRequests.add(body);

        // Mock the upload local file operation.
        mock.raw().on("PUT", cosFile(FILE_NAME)).replies(cosFileContent(null));

        // Mock extracted file result.
        mock.raw().on("GET", cosFile(outputFileName)).replies(cosFileContent("Hello"));

        if (deleteUploadedFile) {
            // Mock delete uploaded file.
            mock.raw().on("DELETE", cosFile(FILE_NAME)).replies(cosFileContent(null));
        }

        if (deleteOutputFile) {
            // Mock delete extracted file.
            mock.raw().on("DELETE", cosFile(outputFileName)).replies(cosFileContent(null));
        }

        // Mock start extraction.
        mock.raw().on("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)
                .reply(200, job(body, PROCESS_EXTRACTION_ID, "submitted"));

        // Mock result extraction.
        mock.raw().on("GET", URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID))
                .reply(200, job(body, PROCESS_EXTRACTION_ID, "completed"));
    }

    /**
     * Checks what the stubs matched on: the IAM token on every request, a dated version on the watsonx.ai requests,
     * and one of the expected bodies on every extraction request.
     */
    private void assertStubbedRequests() {
        assertThat(apiRequests()).allSatisfy(request -> {
            if (request.path().startsWith(COS_PATH)) {
                assertThat(request.query()).isNull();
                assertThat(request.header("Authorization")).isEqualTo("Bearer " + BEARER_TOKEN);
            } else {
                assertWatsonxRequest(request, request.path(), BEARER_TOKEN);
            }
        });
        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).allSatisfy(request -> assertThat(
                expectedRequests).anySatisfy(expected -> assertJsonBody(request, expected)));
    }

    private static String cosFile(String fileName) {
        return COS_PATH + "/" + BUCKET_NAME + "/" + fileName;
    }

    /**
     * A Cloud Object Storage response, which the stubs gave as XML.
     */
    private static RawResponse cosFileContent(String content) {
        return RawResponse.of(200, Map.of("Content-Type", "application/xml"), content);
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

    /**
     * A {@code TextExtractionResponse} for the given request.
     */
    private static String job(TextExtractionRequest request, String id, String status) {
        return """
                {
                  "metadata": {
                    "id": "%s",
                    "created_at": "2023-05-02T16:27:51Z",
                    "project_id": "%s",
                    "name": "extract"
                  },
                  "entity": {
                    "document_reference": {
                      "type": "connection_asset",
                      "connection": {
                        "id": "%s"
                      },
                      "location": {
                        "file_name": "%s",
                        "bucket": "%s"
                      }
                    },
                    "results_reference": {
                      "type": "connection_asset",
                      "connection": {
                        "id": "%s"
                      },
                      "location": {
                        "file_name": "%s",
                        "bucket": "%s"
                      }
                    },
                    "results": {
                      "status": "%s",
                      "number_pages_processed": 1,
                      "running_at": "2023-05-02T16:28:03Z",
                      "completed_at": "2023-05-02T16:28:03Z"
                    }
                  }
                }""".formatted(
                id,
                request.projectId(),
                request.documentReference().connection().id(),
                request.documentReference().location().fileName(),
                request.documentReference().location().bucket(),
                request.resultsReference().connection().id(),
                request.resultsReference().location().fileName(),
                request.resultsReference().location().bucket(),
                status);
    }

    /**
     * A failed {@code TextExtractionResponse} for the given request.
     */
    private static String failedJob(TextExtractionRequest request, String id) {
        return """
                {
                      "metadata": {
                        "id": "%s",
                        "created_at": "2023-05-02T16:27:51Z",
                        "project_id": "%s",
                        "name": "extract"
                      },
                      "entity": {
                        "document_reference": {
                          "type": "connection_asset",
                          "connection": {
                            "id": "%s"
                          },
                          "location": {
                            "file_name": "%s"
                          }
                        },
                        "results_reference": {
                          "type": "connection_asset",
                          "connection": {
                            "id": "%s"
                          },
                          "location": {
                            "file_name": "%s"
                          }
                        },
                        "results": {
                            "error": {
                                "code": "file_download_error",
                                "message": "error message"
                            },
                            "number_pages_processed": 0,
                            "status": "failed"
                        }
                      }
                    }""".formatted(
                id,
                request.projectId(),
                request.documentReference().connection().id(),
                request.documentReference().location().fileName(),
                request.resultsReference().connection().id(),
                request.resultsReference().location().fileName());
    }
}
