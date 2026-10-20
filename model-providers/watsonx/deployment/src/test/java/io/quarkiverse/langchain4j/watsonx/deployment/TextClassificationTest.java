package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.COS_PATH;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_TEXT_CLASSIFICATION_START_API;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.textprocessing.Status;
import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationDeleteParameters;
import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationFetchParameters;
import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationService;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkus.test.QuarkusExtensionTest;

public class TextClassificationTest extends WatsonxMockTestBase {

    static String CONNECTION_ID = "connection_id";
    static String BUCKET_NAME = "my-bucket";
    static String FILE_NAME = "test.pdf";
    static String PROCESS_CLASSIFICATION_ID = "my-id";
    static String COS_FILE = COS_PATH + "/my-bucket/test.pdf";

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            // Cloud Object Storage has its own host, which the mock does not take over
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.cos-url",
                    "${quarkus.langchain4j.mock-api.url}" + COS_PATH)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.document-reference.connection",
                    CONNECTION_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.document-reference.bucket-name",
                    BUCKET_NAME)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(WatsonxTestUtil.class)
                    .addAsResource(FILE_NAME));

    @Inject
    TextClassificationService classificationService;

    @Test
    void should_start_classification() throws Exception {

        var RESULT = Files.readString(Path.of(ClassLoader.getSystemResource("classification_response.json").toURI()));

        String expectedBody = """
                {
                    "project_id": "%s",
                    "document_reference": {
                        "type": "connection_asset",
                        "connection": {
                            "id": "connection_id"
                        },
                        "location": {
                            "file_name": "test.pdf",
                            "bucket": "my-bucket"
                        }
                    }
                }""".formatted(PROJECT_ID);
        mock.raw().on("POST", URL_WATSONX_TEXT_CLASSIFICATION_START_API).reply(200, RESULT);

        var result = classificationService.startClassification("test.pdf");
        assertNotNull(result);

        List<CapturedRequest> requests = apiRequests();
        assertThat(requests).hasSize(1);
        assertWatsonxRequest(requests.get(0), URL_WATSONX_TEXT_CLASSIFICATION_START_API);
        assertJsonBody(requests.get(0), expectedBody);
    }

    @Test
    void should_fetch_classification_request() throws Exception {

        var JOB = Files.readString(Path.of(ClassLoader.getSystemResource("classification_job.json").toURI()));

        mock.raw().on("GET", "/watsonx/ml/v1/text/classifications/id").replies(
                RawResponse.json(200, JOB.formatted(Status.SUBMITTED.value())),
                RawResponse.json(200, "{}"),
                RawResponse.json(200, "{}"));

        var response = classificationService.fetchClassificationRequest("id");
        assertEquals(response.entity().results().status(), Status.SUBMITTED.value());

        var projectId = URLEncoder.encode("new-project-id", Charset.defaultCharset());

        var parameters = TextClassificationFetchParameters.builder()
                .projectId("new-project-id")
                .transactionId("my-transaction-id")
                .build();

        response = classificationService.fetchClassificationRequest("id", parameters);
        assertNotNull(response);

        var spaceId = URLEncoder.encode("new-space-id", Charset.defaultCharset());

        parameters = TextClassificationFetchParameters.builder()
                .spaceId("new-space-id")
                .transactionId("my-transaction-id")
                .build();

        response = classificationService.fetchClassificationRequest("id", parameters);
        assertNotNull(response);

        List<CapturedRequest> requests = apiRequests();
        assertThat(requests).hasSize(3);
        assertWatsonxRequest(requests.get(0), "/watsonx/ml/v1/text/classifications/id");
        assertThat(requests.get(0).method()).isEqualTo("GET");
        assertThat(queryParams(requests.get(0))).containsEntry("project_id", PROJECT_ID);
        assertThat(requests.get(0).header("Accept")).isEqualTo("application/json");
        assertWatsonxRequest(requests.get(1), "/watsonx/ml/v1/text/classifications/id");
        assertThat(requests.get(1).method()).isEqualTo("GET");
        assertThat(queryParams(requests.get(1))).containsEntry("project_id", projectId);
        assertThat(requests.get(1).header("Accept")).isEqualTo("application/json");
        assertThat(requests.get(1).header("X-Global-Transaction-Id")).isEqualTo("my-transaction-id");
        assertWatsonxRequest(requests.get(2), "/watsonx/ml/v1/text/classifications/id");
        assertThat(requests.get(2).method()).isEqualTo("GET");
        assertThat(queryParams(requests.get(2))).containsEntry("space_id", spaceId);
        assertThat(requests.get(2).header("Accept")).isEqualTo("application/json");
        assertThat(requests.get(2).header("X-Global-Transaction-Id")).isEqualTo("my-transaction-id");
    }

    @Test
    void should_delete_classification_request() {

        mock.raw().on("DELETE", "/watsonx/ml/v1/text/classifications/id").replies(
                RawResponse.of(204, Map.of(), null),
                RawResponse.json(404, """
                        {
                            "trace": "db2821f494a629c614616e458c85de36",
                            "errors": [
                                {
                                    "code": "text_classification_event_does_not_exist",
                                    "message": "Text classification request does not exist."
                                }
                            ]
                        }"""),
                RawResponse.of(204, Map.of(), null),
                RawResponse.of(204, Map.of(), null));

        assertTrue(classificationService.deleteRequest("id"));

        assertFalse(classificationService.deleteRequest("id"));

        var projectId = URLEncoder.encode("new-project-id", Charset.defaultCharset());

        var parameters = TextClassificationDeleteParameters.builder()
                .projectId("new-project-id")
                .hardDelete(true)
                .transactionId("my-transaction-id")
                .build();

        assertTrue(classificationService.deleteRequest("id", parameters));

        var spaceId = URLEncoder.encode("new-space-id", Charset.defaultCharset());

        parameters = TextClassificationDeleteParameters.builder()
                .spaceId("new-space-id")
                .build();

        assertTrue(classificationService.deleteRequest("id", parameters));

        List<CapturedRequest> requests = apiRequests();
        assertThat(requests).hasSize(4);
        assertWatsonxRequest(requests.get(0), "/watsonx/ml/v1/text/classifications/id");
        assertThat(requests.get(0).method()).isEqualTo("DELETE");
        assertThat(queryParams(requests.get(0))).containsEntry("project_id", PROJECT_ID);
        assertWatsonxRequest(requests.get(1), "/watsonx/ml/v1/text/classifications/id");
        assertThat(requests.get(1).method()).isEqualTo("DELETE");
        assertThat(queryParams(requests.get(1))).containsEntry("project_id", PROJECT_ID);
        assertWatsonxRequest(requests.get(2), "/watsonx/ml/v1/text/classifications/id");
        assertThat(requests.get(2).method()).isEqualTo("DELETE");
        assertThat(queryParams(requests.get(2))).containsEntry("project_id", projectId);
        assertThat(requests.get(2).header("X-Global-Transaction-Id")).isEqualTo("my-transaction-id");
        assertWatsonxRequest(requests.get(3), "/watsonx/ml/v1/text/classifications/id");
        assertThat(requests.get(3).method()).isEqualTo("DELETE");
        assertThat(queryParams(requests.get(3))).containsEntry("space_id", spaceId);
    }

    @Test
    void should_delete_file() throws Exception {

        // the client retries a delete refused with access denied, sending the same token again
        mock.raw().on("DELETE", COS_FILE).replies(
                RawResponse.of(403, Map.of("Content-Type", "application/xml"), """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Error>
                            <Code>AccessDenied</Code>
                            <Message>Access Denied</Message>
                            <Resource>/andreaproject-donotdelete-pr-xnran4g4ptd1wo/ciao.pdf</Resource>
                            <RequestId>df887c2b-43c3-4933-a3a1-b0e19e7c2231</RequestId>
                            <httpStatusCode>403</httpStatusCode>
                        </Error>"""),
                RawResponse.of(204, Map.of(), null));

        assertTrue(classificationService.deleteFile("my-bucket", "test.pdf"));
        Thread.sleep(500);
        assertThat(apiRequests()).hasSize(2).allSatisfy(request -> {
            assertThat(request.path()).isEqualTo(COS_FILE);
            assertThat(request.query()).isNull();
            assertThat(request.header("Authorization")).isEqualTo("Bearer " + MOCK_TOKEN);
        });
    }

    @Test
    void should_upload_file() throws Exception {

        var file = new File(ClassLoader.getSystemResource("test.pdf").toURI());
        mock.raw().on("PUT", COS_FILE).replies(RawResponse.of(200, Map.of(), null));

        assertTrue(classificationService.uploadFile(file));

        assertThat(apiRequests()).singleElement().satisfies(request -> {
            assertThat(request.path()).isEqualTo(COS_FILE);
            assertThat(request.query()).isNull();
            assertThat(request.header("Authorization")).isEqualTo("Bearer " + MOCK_TOKEN);
        });
    }
}
