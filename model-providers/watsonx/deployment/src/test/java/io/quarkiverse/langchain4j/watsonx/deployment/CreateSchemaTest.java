package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.COS_PATH;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_CREATE_SCHEMA_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_CREATE_SCHEMA_RESULT_API;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.textprocessing.Status;
import com.ibm.watsonx.ai.textprocessing.schema.create.CreateSchemaDeleteParameters;
import com.ibm.watsonx.ai.textprocessing.schema.create.CreateSchemaFetchParameters;
import com.ibm.watsonx.ai.textprocessing.schema.create.CreateSchemaService;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkus.test.QuarkusExtensionTest;

public class CreateSchemaTest extends WatsonxMockTestBase {

    static String CONNECTION_ID = "connection_id";
    static String BUCKET_NAME = "my-bucket";
    static String FILE_NAME = "test.pdf";
    static String SCHEMA_ID = "my-id";
    static String COS_FILE = COS_PATH + "/" + BUCKET_NAME + "/" + FILE_NAME;

    static String RESPONSE = """
            {
                "metadata": {
                    "id": "%s",
                    "created_at": "2023-05-02T16:27:51Z",
                    "project_id": "%s"
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
                    "results": {
                        "status": "%s",
                        "number_pages_processed": 0
                    }
                }
            }""";

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            // Cloud Object Storage has its own host, which the mock does not take over
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.schema.create.cos-url",
                    "${quarkus.langchain4j.mock-api.url}" + COS_PATH)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.schema.create.document-reference.connection",
                    CONNECTION_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.schema.create.document-reference.bucket-name",
                    BUCKET_NAME)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(WatsonxTestUtil.class)
                    .addAsResource(FILE_NAME));

    @Inject
    CreateSchemaService createSchemaService;

    @Test
    void should_start_create_schema() {

        String expectedBody = """
                {
                    "project_id": "%s",
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
                    "parameters": {
                        "ocr_mode": "disabled"
                    }
                }""".formatted(PROJECT_ID, CONNECTION_ID, FILE_NAME, BUCKET_NAME);
        mock.raw().on("POST", URL_WATSONX_CREATE_SCHEMA_API).reply(200,
                RESPONSE.formatted(SCHEMA_ID, PROJECT_ID, CONNECTION_ID, FILE_NAME, BUCKET_NAME,
                        Status.SUBMITTED.value()));

        var response = createSchemaService.startCreateSchema(FILE_NAME);
        assertNotNull(response);
        assertEquals(SCHEMA_ID, response.metadata().id());
        assertEquals(Status.SUBMITTED.value(), response.entity().results().status());

        List<CapturedRequest> requests = apiRequests();
        assertThat(requests).hasSize(1);
        assertWatsonxRequest(requests.get(0), URL_WATSONX_CREATE_SCHEMA_API);
        assertJsonBody(requests.get(0), expectedBody);
    }

    @Test
    void should_fetch_create_schema_request() {

        mock.raw().on("GET", URL_WATSONX_CREATE_SCHEMA_RESULT_API.formatted(SCHEMA_ID)).replies(
                RawResponse.json(200, RESPONSE.formatted(SCHEMA_ID, PROJECT_ID, CONNECTION_ID, FILE_NAME, BUCKET_NAME,
                        Status.COMPLETED.value())),
                RawResponse.json(200, "{}"));

        var response = createSchemaService.fetchRequest(SCHEMA_ID);
        assertEquals(Status.COMPLETED.value(), response.entity().results().status());

        var parameters = CreateSchemaFetchParameters.builder()
                .spaceId("new-space-id")
                .transactionId("my-transaction-id")
                .build();

        assertNotNull(createSchemaService.fetchRequest(SCHEMA_ID, parameters));

        List<CapturedRequest> requests = apiRequests();
        assertThat(requests).hasSize(2);
        assertWatsonxRequest(requests.get(0), URL_WATSONX_CREATE_SCHEMA_RESULT_API.formatted(SCHEMA_ID));
        assertThat(requests.get(0).method()).isEqualTo("GET");
        assertThat(queryParams(requests.get(0))).containsEntry("project_id", PROJECT_ID);
        assertThat(requests.get(0).header("Accept")).isEqualTo("application/json");
        assertWatsonxRequest(requests.get(1), URL_WATSONX_CREATE_SCHEMA_RESULT_API.formatted(SCHEMA_ID));
        assertThat(requests.get(1).method()).isEqualTo("GET");
        assertThat(queryParams(requests.get(1))).containsEntry("space_id", "new-space-id");
        assertThat(requests.get(1).header("Accept")).isEqualTo("application/json");
        assertThat(requests.get(1).header("X-Global-Transaction-Id")).isEqualTo("my-transaction-id");
    }

    @Test
    void should_delete_create_schema_request() {

        mock.raw().on("DELETE", URL_WATSONX_CREATE_SCHEMA_RESULT_API.formatted(SCHEMA_ID)).replies(
                RawResponse.of(204, Map.of(), null),
                RawResponse.of(204, Map.of(), null));

        assertTrue(createSchemaService.deleteRequest(SCHEMA_ID));

        var parameters = CreateSchemaDeleteParameters.builder()
                .spaceId("new-space-id")
                .hardDelete(true)
                .transactionId("my-transaction-id")
                .build();

        assertTrue(createSchemaService.deleteRequest(SCHEMA_ID, parameters));

        List<CapturedRequest> requests = apiRequests();
        assertThat(requests).hasSize(2);
        assertWatsonxRequest(requests.get(0), URL_WATSONX_CREATE_SCHEMA_RESULT_API.formatted(SCHEMA_ID));
        assertThat(requests.get(0).method()).isEqualTo("DELETE");
        assertThat(queryParams(requests.get(0))).containsEntry("project_id", PROJECT_ID);
        assertWatsonxRequest(requests.get(1), URL_WATSONX_CREATE_SCHEMA_RESULT_API.formatted(SCHEMA_ID));
        assertThat(requests.get(1).method()).isEqualTo("DELETE");
        assertThat(queryParams(requests.get(1))).containsEntry("space_id", "new-space-id");
        assertThat(queryParams(requests.get(1))).containsEntry("hard_delete", "true");
        assertThat(requests.get(1).header("X-Global-Transaction-Id")).isEqualTo("my-transaction-id");
    }

    @Test
    void should_return_false_when_create_schema_request_does_not_exist() {

        mock.raw().on("DELETE", URL_WATSONX_CREATE_SCHEMA_RESULT_API.formatted(SCHEMA_ID)).replies(RawResponse.json(404, """
                {
                    "trace": "db2821f494a629c614616e458c85de36",
                    "errors": [
                        {
                            "code": "schema_event_does_not_exist",
                            "message": "Schema request does not exist."
                        }
                    ]
                }"""));

        assertFalse(createSchemaService.deleteRequest(SCHEMA_ID));

        List<CapturedRequest> requests = apiRequests();
        assertThat(requests).hasSize(1);
        assertWatsonxRequest(requests.get(0), URL_WATSONX_CREATE_SCHEMA_RESULT_API.formatted(SCHEMA_ID));
        assertThat(requests.get(0).method()).isEqualTo("DELETE");
        assertThat(queryParams(requests.get(0))).containsEntry("project_id", PROJECT_ID);
    }

    @Test
    void should_propagate_error_on_start_create_schema() {

        mock.raw().on("POST", URL_WATSONX_CREATE_SCHEMA_API).reply(400, """
                {
                    "trace": "db2821f494a629c614616e458c85de36",
                    "errors": [
                        {
                            "code": "invalid_request_entity",
                            "message": "Missing document reference."
                        }
                    ]
                }""");

        var ex = assertThrows(WatsonxException.class, () -> createSchemaService.startCreateSchema(FILE_NAME));
        assertEquals(400, ex.statusCode());

        List<CapturedRequest> requests = apiRequests();
        assertThat(requests).hasSize(1);
        assertWatsonxRequest(requests.get(0), URL_WATSONX_CREATE_SCHEMA_API);
    }

    @Test
    void should_upload_file() throws Exception {

        var file = new File(ClassLoader.getSystemResource(FILE_NAME).toURI());
        mock.raw().on("PUT", COS_FILE).replies(RawResponse.of(200, Map.of(), null));

        assertTrue(createSchemaService.uploadFile(file));

        assertThat(apiRequests()).singleElement().satisfies(request -> {
            assertThat(request.path()).isEqualTo(COS_FILE);
            assertThat(request.query()).isNull();
            assertThat(request.header("Authorization")).isEqualTo("Bearer " + MOCK_TOKEN);
        });
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
                            <httpStatusCode>403</httpStatusCode>
                        </Error>"""),
                RawResponse.of(204, Map.of(), null));

        assertTrue(createSchemaService.deleteFile(BUCKET_NAME, FILE_NAME));
        assertThat(apiRequests()).hasSize(2).allSatisfy(request -> {
            assertThat(request.path()).isEqualTo(COS_FILE);
            assertThat(request.query()).isNull();
            assertThat(request.header("Authorization")).isEqualTo("Bearer " + MOCK_TOKEN);
        });
    }
}
