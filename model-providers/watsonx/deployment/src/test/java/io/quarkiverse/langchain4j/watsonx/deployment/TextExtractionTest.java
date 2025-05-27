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
import static io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.Type.HTML;
import static io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.Type.JSON;
import static io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.Type.MD;
import static io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.Type.PAGE_IMAGES;
import static io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.Type.PLAIN_TEXT;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.GRANT_TYPE;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_COS_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_TEXT_EXTRACTION_RESULT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_TEXT_EXTRACTION_START_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.VERSION;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.stubbing.Scenario;

import io.quarkiverse.langchain4j.watsonx.bean.CosError;
import io.quarkiverse.langchain4j.watsonx.bean.CosError.Code;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.EmbeddedImages;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.Mode;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.OCR;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.TextExtractionDataReference;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.TextExtractionParameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionResponse;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionResponse.Status;
import io.quarkiverse.langchain4j.watsonx.bean.WatsonxError;
import io.quarkiverse.langchain4j.watsonx.client.COSRestApi;
import io.quarkiverse.langchain4j.watsonx.client.WatsonxRestApi;
import io.quarkiverse.langchain4j.watsonx.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.exception.COSException;
import io.quarkiverse.langchain4j.watsonx.exception.TextExtractionException;
import io.quarkiverse.langchain4j.watsonx.exception.WatsonxException;
import io.quarkiverse.langchain4j.watsonx.runtime.TextExtraction;
import io.quarkiverse.langchain4j.watsonx.runtime.TextExtraction.Parameters;
import io.quarkiverse.langchain4j.watsonx.runtime.TokenGenerationCache;
import io.quarkiverse.langchain4j.watsonx.runtime.TokenGenerator;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.test.QuarkusUnitTest;

public class TextExtractionTest extends WireMockAbstract {

    static String CONNECTION_ID = "my-connection-id";
    static String BUCKET_NAME = "my-bucket-name";
    static String FILE_NAME = "test.pdf";
    static String PROCESS_EXTRACTION_ID = "my-id";

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(WireMockUtil.class)
                    .addAsResource(FILE_NAME));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType())
                .response(BEARER_TOKEN, new Date())
                .build();
    }

    static TokenGenerator tokenGenerator;
    static WatsonxRestApi watsonxRestApi;
    static COSRestApi cosRestApi;
    static TextExtraction textExtraction;

    @BeforeAll
    static void init() throws MalformedURLException {
        tokenGenerator = TokenGenerationCache.getOrCreateTokenGenerator(
                API_KEY,
                URI.create(URL_IAM_SERVER).toURL(),
                GRANT_TYPE,
                Duration.ofSeconds(10));

        watsonxRestApi = QuarkusRestClientBuilder.newBuilder()
                .baseUrl(URI.create(URL_WATSONX_SERVER).toURL())
                .clientHeadersFactory(new BearerTokenHeaderFactory(tokenGenerator))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .loggingScope(LoggingScope.REQUEST_RESPONSE)
                .build(WatsonxRestApi.class);

        cosRestApi = QuarkusRestClientBuilder.newBuilder()
                .baseUrl(URI.create(URL_COS_SERVER).toURL())
                .clientHeadersFactory(new BearerTokenHeaderFactory(tokenGenerator))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build(COSRestApi.class);

        textExtraction = new TextExtraction(
                new TextExtraction.Reference(CONNECTION_ID, BUCKET_NAME),
                new TextExtraction.Reference(CONNECTION_ID, BUCKET_NAME),
                PROJECT_ID,
                null,
                VERSION,
                cosRestApi, watsonxRestApi);
    }

    @Test
    void extractAndFetchTest() throws Exception {

        String fileAlreadyUploaded = "test.pdf";
        String outputFileName = fileAlreadyUploaded.replace(".pdf", ".md");

        TextExtractionRequest body = createDefaultRequest();

        mockServers(body, outputFileName, false, false);

        String textExtracted = textExtraction.extractAndFetch(fileAlreadyUploaded);
        assertEquals("Hello", textExtracted);

        textExtracted = textExtraction.extractAndFetch(fileAlreadyUploaded, Parameters.builder().build());
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

        textExtracted = textExtraction.uploadExtractAndFetch(inputStream, fileName, Parameters.builder().build());
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

        textExtracted = textExtraction.uploadExtractAndFetch(file, Parameters.builder().build());
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

        TextExtractionException ex = assertThrows(TextExtractionException.class,
                () -> textExtraction.uploadExtractAndFetch(file));
        assertEquals(ex.getCode(), "file_not_found");
        assertTrue(ex.getCause() instanceof FileNotFoundException);

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

        String processId = textExtraction.startExtraction(fileAlreadyUploaded);
        assertEquals(PROCESS_EXTRACTION_ID, processId);

        processId = textExtraction.startExtraction(fileAlreadyUploaded, Parameters.builder().build());
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

        String processId = textExtraction.uploadAndStartExtraction(file);
        assertEquals(PROCESS_EXTRACTION_ID, processId);

        processId = textExtraction.uploadAndStartExtraction(file, Parameters.builder().build());
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
        assertEquals(ex.getCode(), "file_not_found");
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

        String processId = textExtraction.uploadAndStartExtraction(inputStream, fileName);
        assertEquals(PROCESS_EXTRACTION_ID, processId);

        processId = textExtraction.uploadAndStartExtraction(inputStream, fileName, Parameters.builder().build());
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

        String outputFileName = "myNewOutput.json";
        File file = new File(TextExtractionTest.class.getClassLoader().getResource(FILE_NAME).toURI());

        TextExtractionRequest body = TextExtractionRequest.builder()
                .documentReference(TextExtractionDataReference.of(CONNECTION_ID, FILE_NAME, BUCKET_NAME))
                .resultsReference(TextExtractionDataReference.of(CONNECTION_ID, outputFileName, BUCKET_NAME))
                .parameters(new TextExtractionParameters(List.of(JSON), null, null, null, null, null, null))
                .projectId(PROJECT_ID)
                .spaceId(null)
                .build();

        mockServers(body, outputFileName, true, true);

        Parameters options = Parameters.builder()
                .outputFileName(outputFileName)
                .removeOutputFile(true)
                .removeUploadedFile(true)
                .types(JSON)
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

        TextExtractionRequest body = TextExtractionRequest.builder()
                .documentReference(TextExtractionDataReference.of(CONNECTION_ID, "test", BUCKET_NAME))
                .resultsReference(TextExtractionDataReference.of(CONNECTION_ID, outputFileName, BUCKET_NAME))
                .parameters(new TextExtractionParameters(List.of(MD), null, null, null, null, null, null))
                .projectId(PROJECT_ID)
                .spaceId(null)
                .build();

        mockServers(body, outputFileName, false, false);

        textExtraction.startExtraction("test");
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

        TextExtractionRequest body = TextExtractionRequest.builder()
                .documentReference(TextExtractionDataReference.of(CONNECTION_ID, FILE_NAME, BUCKET_NAME))
                .resultsReference(TextExtractionDataReference.of(CONNECTION_ID, outputFileName, BUCKET_NAME))
                .parameters(new TextExtractionParameters(List.of(JSON), null, null, null, null, null, null))
                .projectId(PROJECT_ID)
                .spaceId(null)
                .build();

        mockServers(body, outputFileName, false, false);

        textExtraction.startExtraction(FILE_NAME, Parameters.builder().types(JSON).build());
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

        TextExtractionRequest body = TextExtractionRequest.builder()
                .documentReference(TextExtractionDataReference.of(CONNECTION_ID, FILE_NAME, BUCKET_NAME))
                .resultsReference(TextExtractionDataReference.of(CONNECTION_ID, outputFileName, BUCKET_NAME))
                .parameters(new TextExtractionParameters(List.of(PLAIN_TEXT), null, null, null, null, null, null))
                .projectId(PROJECT_ID)
                .spaceId(null)
                .build();

        mockServers(body, outputFileName, false, false);

        textExtraction.startExtraction(FILE_NAME, Parameters.builder().types(PLAIN_TEXT).build());
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

        TextExtractionRequest body = TextExtractionRequest.builder()
                .documentReference(TextExtractionDataReference.of(CONNECTION_ID, FILE_NAME, BUCKET_NAME))
                .resultsReference(TextExtractionDataReference.of(CONNECTION_ID, outputFolderName, BUCKET_NAME))
                .parameters(new TextExtractionParameters(List.of(PAGE_IMAGES), null, null, null, null, null, null))
                .projectId(PROJECT_ID)
                .spaceId(null)
                .build();

        mockServers(body, outputFolderName, false, false);

        textExtraction.startExtraction(FILE_NAME, Parameters.builder().types(PAGE_IMAGES).build());
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

        TextExtractionRequest body = TextExtractionRequest.builder()
                .documentReference(TextExtractionDataReference.of(CONNECTION_ID, FILE_NAME, BUCKET_NAME))
                .resultsReference(TextExtractionDataReference.of(CONNECTION_ID, outputFolderName, BUCKET_NAME))
                .parameters(new TextExtractionParameters(List.of(MD, JSON, PLAIN_TEXT, PAGE_IMAGES, HTML), null, null, null,
                        null, null, null))
                .projectId(PROJECT_ID)
                .spaceId(null)
                .build();

        mockServers(body, outputFolderName, false, false);

        textExtraction.startExtraction(FILE_NAME,
                Parameters.builder().types(MD, JSON, PLAIN_TEXT, PAGE_IMAGES, HTML).build());
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

        TextExtractionRequest body = TextExtractionRequest.builder()
                .documentReference(TextExtractionDataReference.of(CONNECTION_ID, FILE_NAME, BUCKET_NAME))
                .resultsReference(TextExtractionDataReference.of(CONNECTION_ID, outputFolderName, BUCKET_NAME))
                .parameters(new TextExtractionParameters(List.of(MD, JSON, PLAIN_TEXT, PAGE_IMAGES, HTML), Mode.STANDARD,
                        OCR.DISABLED, false,
                        EmbeddedImages.DISABLED, 16, false))
                .projectId(PROJECT_ID)
                .spaceId(null)
                .build();

        mockServers(body, outputFolderName, false, false);

        textExtraction.startExtraction(
                FILE_NAME,
                Parameters.builder()
                        .types(MD, JSON, PLAIN_TEXT, PAGE_IMAGES, HTML)
                        .mode(Mode.STANDARD)
                        .ocr(OCR.DISABLED)
                        .autoRotationCorrection(false)
                        .embeddedImages(EmbeddedImages.DISABLED)
                        .dpi(16)
                        .outputTokensAndBbox(false)
                        .build());

        body = TextExtractionRequest.builder()
                .documentReference(TextExtractionDataReference.of(CONNECTION_ID, FILE_NAME, BUCKET_NAME))
                .resultsReference(TextExtractionDataReference.of(CONNECTION_ID, outputFolderName, BUCKET_NAME))
                .parameters(
                        new TextExtractionParameters(List.of(MD, JSON, PLAIN_TEXT, PAGE_IMAGES, HTML), Mode.HIGH_QUALITY,
                                OCR.ENABLED, true,
                                EmbeddedImages.ENABLED_PLACEHOLDER, 32, true))
                .projectId(PROJECT_ID)
                .spaceId(null)
                .build();

        mockServers(body, outputFolderName, false, false);

        textExtraction.startExtraction(
                FILE_NAME,
                Parameters.builder()
                        .types(MD, JSON, PLAIN_TEXT, PAGE_IMAGES, HTML)
                        .mode(Mode.HIGH_QUALITY)
                        .ocr(OCR.ENABLED)
                        .autoRotationCorrection(true)
                        .embeddedImages(EmbeddedImages.ENABLED_PLACEHOLDER)
                        .dpi(32)
                        .outputTokensAndBbox(true)
                        .build());

        watsonxServer.verify(2, postRequestedFor(urlPathEqualTo("/ml/v1/text/extractions")));
    }

    @Test
    void fetchWithMultipleTypes() throws URISyntaxException {

        String outputFolderName = "myFolder/";

        TextExtractionRequest body = TextExtractionRequest.builder()
                .documentReference(TextExtractionDataReference.of(CONNECTION_ID, FILE_NAME, BUCKET_NAME))
                .resultsReference(TextExtractionDataReference.of(CONNECTION_ID, outputFolderName, BUCKET_NAME))
                .parameters(new TextExtractionParameters(List.of(MD, JSON, PLAIN_TEXT, PAGE_IMAGES, HTML), Mode.STANDARD,
                        OCR.DISABLED, false,
                        EmbeddedImages.DISABLED, 16, false))
                .projectId(PROJECT_ID)
                .spaceId(null)
                .build();

        mockServers(body, outputFolderName, false, false);
        var ex = assertThrows(TextExtractionException.class, () -> {
            textExtraction.extractAndFetch(
                    FILE_NAME,
                    Parameters.builder()
                            .outputFileName("myFolder/")
                            .types(MD, JSON, PLAIN_TEXT, PAGE_IMAGES, HTML)
                            .mode(Mode.STANDARD)
                            .ocr(OCR.DISABLED)
                            .autoRotationCorrection(false)
                            .embeddedImages(EmbeddedImages.DISABLED)
                            .dpi(16)
                            .outputTokensAndBbox(false)
                            .build());
        });
        assertEquals("fetch_operation_not_allowed", ex.getCode());
        assertEquals("The fetch operation cannot be executed if more than one file is to be generated",
                ex.getMessage());

        File file = new File(TextExtractionTest.class.getClassLoader().getResource(FILE_NAME).toURI());

        ex = assertThrows(TextExtractionException.class, () -> {
            textExtraction.uploadExtractAndFetch(
                    file,
                    Parameters.builder()
                            .outputFileName("myFolder/")
                            .types(MD, JSON, PLAIN_TEXT, PAGE_IMAGES, HTML)
                            .mode(Mode.STANDARD)
                            .ocr(OCR.DISABLED)
                            .autoRotationCorrection(false)
                            .embeddedImages(EmbeddedImages.DISABLED)
                            .dpi(16)
                            .outputTokensAndBbox(false)
                            .build());
        });
        assertEquals("fetch_operation_not_allowed", ex.getCode());
        assertEquals("The fetch operation cannot be executed if more than one file is to be generated",
                ex.getMessage());

        InputStream inputStream = TextExtractionTest.class.getClassLoader().getResourceAsStream(FILE_NAME);

        ex = assertThrows(TextExtractionException.class, () -> {
            textExtraction.uploadExtractAndFetch(
                    inputStream,
                    FILE_NAME,
                    Parameters.builder()
                            .outputFileName("myFolder/")
                            .types(MD, JSON, PLAIN_TEXT, PAGE_IMAGES, HTML)
                            .mode(Mode.STANDARD)
                            .ocr(OCR.DISABLED)
                            .autoRotationCorrection(false)
                            .embeddedImages(EmbeddedImages.DISABLED)
                            .dpi(16)
                            .outputTokensAndBbox(false)
                            .build());
        });
        assertEquals("fetch_operation_not_allowed", ex.getCode());
        assertEquals("The fetch operation cannot be executed if more than one file is to be generated",
                ex.getMessage());
    }

    @Test
    void fetchPageImages() throws URISyntaxException {
        String outputFolderName = "myFolder/";
        InputStream inputStream = TextExtractionTest.class.getClassLoader().getResourceAsStream(FILE_NAME);
        File file = new File(TextExtractionTest.class.getClassLoader().getResource(FILE_NAME).toURI());

        TextExtractionRequest body = TextExtractionRequest.builder()
                .documentReference(TextExtractionDataReference.of(CONNECTION_ID, FILE_NAME, BUCKET_NAME))
                .resultsReference(TextExtractionDataReference.of(CONNECTION_ID, outputFolderName, BUCKET_NAME))
                .parameters(new TextExtractionParameters(List.of(PAGE_IMAGES), null,
                        null, null,
                        null, null, null))
                .projectId(PROJECT_ID)
                .spaceId(null)
                .build();

        mockServers(body, outputFolderName, false, false);
        var ex = assertThrows(TextExtractionException.class, () -> {
            textExtraction.extractAndFetch(
                    FILE_NAME,
                    Parameters.builder()
                            .outputFileName("myFolder/")
                            .types(PAGE_IMAGES)
                            .build());
        });
        assertEquals("fetch_operation_not_allowed", ex.getCode());
        assertEquals("The fetch operation cannot be executed for the type \"page_images\"",
                ex.getMessage());

        ex = assertThrows(TextExtractionException.class, () -> {
            textExtraction.uploadExtractAndFetch(
                    file,
                    Parameters.builder()
                            .outputFileName("myFolder/")
                            .types(PAGE_IMAGES)
                            .build());
        });
        assertEquals("fetch_operation_not_allowed", ex.getCode());
        assertEquals("The fetch operation cannot be executed for the type \"page_images\"",
                ex.getMessage());

        ex = assertThrows(TextExtractionException.class, () -> {
            textExtraction.uploadExtractAndFetch(
                    inputStream,
                    FILE_NAME,
                    Parameters.builder()
                            .outputFileName("myFolder/")
                            .types(PAGE_IMAGES)
                            .build());
        });
        assertEquals("fetch_operation_not_allowed", ex.getCode());
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
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID, VERSION), 200)
                .response(body, PROCESS_EXTRACTION_ID, "running")
                .scenario("firstIteration", "secondIteration")
                .build();

        mockTextExtractionBuilder(GET,
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID, VERSION), 200)
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
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID, VERSION), 200)
                .response(body, PROCESS_EXTRACTION_ID, "running")
                .scenario("firstIteration", "secondIteration")
                .build();

        mockTextExtractionBuilder(GET,
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID, VERSION), 200)
                .response(body, PROCESS_EXTRACTION_ID, "completed")
                .scenario("secondIteration", Scenario.STARTED)
                .build();

        mockCosBuilder(GET, BUCKET_NAME, outputFileName, 200)
                .response("Hello")
                .build();

        Parameters options = Parameters.builder()
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

        Parameters parameters = Parameters.builder()
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
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID, VERSION), 200)
                .response(request, PROCESS_EXTRACTION_ID, "running")
                .scenario(Scenario.STARTED, "failed")
                .build();

        mockTextExtractionBuilder(GET,
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID, VERSION), 200)
                .failResponse(request, PROCESS_EXTRACTION_ID)
                .scenario("failed", Scenario.STARTED)
                .build();

        var ex = assertThrows(
                TextExtractionException.class,
                () -> textExtraction.extractAndFetch(FILE_NAME, parameters));

        assertEquals(ex.getCode(), "file_download_error");
        assertEquals(ex.getMessage(), "error message");

        ex = assertThrows(
                TextExtractionException.class,
                () -> textExtraction.uploadExtractAndFetch(file, parameters));

        assertEquals(ex.getCode(), "file_download_error");
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

        CosError cosError = new CosError();
        cosError.setCode(Code.NO_SUCH_BUCKET);
        cosError.setHttpStatusCode(404);
        cosError.setMessage("The specified bucket does not exist.");
        cosError.setRequestId("my-request-id");
        cosError.setResource("/my-bucket-name/test.pdf");

        var ex = assertThrows(
                COSException.class,
                () -> textExtraction.uploadAndStartExtraction(file),
                "The specified bucket does not exist.");

        assertEquals(cosError, ex.details());

        ex = assertThrows(
                COSException.class,
                () -> textExtraction.uploadExtractAndFetch(file),
                "The specified bucket does not exist.");

        assertEquals(cosError, ex.details());

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
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID, VERSION), 200)
                .response(body, PROCESS_EXTRACTION_ID, "completed")
                .build();

        CosError cosError = new CosError();
        cosError.setCode(Code.NO_SUCH_KEY);
        cosError.setHttpStatusCode(404);
        cosError.setMessage("The specified key does not exist.");
        cosError.setRequestId("my-request-id");
        cosError.setResource("/my-bucket-name/test.pdf");

        var ex = assertThrows(
                COSException.class,
                () -> textExtraction.extractAndFetch(FILE_NAME),
                "The specified key does not exist.");

        assertEquals(cosError, ex.details());

        ex = assertThrows(
                COSException.class,
                () -> textExtraction.uploadExtractAndFetch(file),
                "The specified key does not exist.");

        assertEquals(cosError, ex.details());

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
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID, VERSION), 404)
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
                () -> textExtraction.checkExtractionStatus(PROCESS_EXTRACTION_ID));
        assertEquals(WatsonxError.Code.TEXT_EXTRACTION_EVENT_DOES_NOT_EXIST,
                ex.details().errors().get(0).codeToEnum().get());
    }

    @Test
    void checkExtractionStatusTest() throws TextExtractionException {

        TextExtractionRequest request = createDefaultRequest();

        mockTextExtractionBuilder(GET,
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID, VERSION), 200)
                .response(request, PROCESS_EXTRACTION_ID, "completed")
                .build();

        TextExtractionResponse response = textExtraction.checkExtractionStatus(PROCESS_EXTRACTION_ID);
        assertEquals(request.documentReference(), response.entity().documentReference());
        assertEquals(request.resultsReference(), response.entity().resultsReference());
        assertEquals(PROCESS_EXTRACTION_ID, response.metadata().id());
        assertEquals(PROJECT_ID, response.metadata().projectId());
        assertNotNull(response.metadata().createdAt());
        assertEquals(Status.COMPLETED, response.entity().results().status());
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

        TextExtractionRequest request = TextExtractionRequest.builder()
                .documentReference(TextExtractionDataReference.of(NEW_CONNECTION_ID, FILE_NAME, NEW_BUCKET_NAME))
                .resultsReference(TextExtractionDataReference.of(NEW_CONNECTION_ID, outputFileName, NEW_BUCKET_NAME))
                .parameters(new TextExtractionParameters(List.of(MD), null, null, null, null, null, null))
                .projectId(PROJECT_ID)
                .spaceId(null)
                .build();

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
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID, VERSION), 200)
                .response(request, PROCESS_EXTRACTION_ID, "completed")
                .build();

        Parameters options = Parameters.builder()
                .documentReference(new TextExtraction.Reference(NEW_CONNECTION_ID, NEW_BUCKET_NAME))
                .outputFileName(outputFileName)
                .removeOutputFile(false)
                .removeUploadedFile(false)
                .resultsReference(new TextExtraction.Reference(NEW_CONNECTION_ID, NEW_BUCKET_NAME))
                .types(MD)
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
        var ex = assertThrows(COSException.class, () -> textExtraction.deleteFile(BUCKET_NAME, FILE_NAME));
        assertEquals(ex.details().getCode(), Code.NO_SUCH_BUCKET);

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
                URL_WATSONX_TEXT_EXTRACTION_RESULT_API.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID, VERSION), 200)
                .response(body, PROCESS_EXTRACTION_ID, "completed")
                .build();
    }

    private TextExtractionRequest createDefaultRequest() {
        return TextExtractionRequest.builder()
                .documentReference(TextExtractionDataReference.of(CONNECTION_ID, FILE_NAME, BUCKET_NAME))
                .resultsReference(
                        TextExtractionDataReference.of(CONNECTION_ID, FILE_NAME.replace(".pdf", ".md"), BUCKET_NAME))
                .parameters(new TextExtractionParameters(List.of(MD), null, null, null, null, null, null))
                .projectId(PROJECT_ID)
                .spaceId(null)
                .build();
    }
}
