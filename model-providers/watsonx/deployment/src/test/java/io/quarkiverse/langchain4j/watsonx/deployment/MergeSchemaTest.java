package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_MERGE_SCHEMA_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_MERGE_SCHEMA_RESULT_API;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.textprocessing.Schema;
import com.ibm.watsonx.ai.textprocessing.Status;
import com.ibm.watsonx.ai.textprocessing.schema.merge.MergeSchemaDeleteParameters;
import com.ibm.watsonx.ai.textprocessing.schema.merge.MergeSchemaFetchParameters;
import com.ibm.watsonx.ai.textprocessing.schema.merge.MergeSchemaService;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkus.test.QuarkusExtensionTest;

public class MergeSchemaTest extends WatsonxMockTestBase {

    static String SCHEMA_ID = "my-id";

    static List<Schema> SCHEMAS = List.of(
            Schema.builder().documentType("Invoice").documentDescription("An invoice").build(),
            Schema.builder().documentType("Receipt").documentDescription("A receipt").build());

    static String RESPONSE = """
            {
                "metadata": {
                    "id": "%s",
                    "created_at": "2023-05-02T16:27:51Z",
                    "project_id": "%s"
                },
                "entity": {
                    "parameters": {
                        "schemas": [
                            {
                                "document_type": "Invoice",
                                "document_description": "An invoice"
                            },
                            {
                                "document_type": "Receipt",
                                "document_description": "A receipt"
                            }
                        ]
                    },
                    "results": {
                        "status": "%s"
                    }
                }
            }""";

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(WatsonxTestUtil.class));

    @Inject
    MergeSchemaService mergeSchemaService;

    @Test
    void should_start_merge_schema() {

        String expectedBody = """
                {
                    "project_id": "%s",
                    "parameters": {
                        "schemas": [
                            {
                                "document_type": "Invoice",
                                "document_description": "An invoice"
                            },
                            {
                                "document_type": "Receipt",
                                "document_description": "A receipt"
                            }
                        ]
                    }
                }""".formatted(PROJECT_ID);
        mock.raw().on("POST", URL_WATSONX_MERGE_SCHEMA_API).reply(200,
                RESPONSE.formatted(SCHEMA_ID, PROJECT_ID, Status.SUBMITTED.value()));

        var response = mergeSchemaService.startMergeSchema(SCHEMAS);
        assertNotNull(response);
        assertEquals(SCHEMA_ID, response.metadata().id());
        assertEquals(Status.SUBMITTED.value(), response.entity().results().status());

        List<CapturedRequest> requests = apiRequests();
        assertThat(requests).hasSize(1);
        assertWatsonxRequest(requests.get(0), URL_WATSONX_MERGE_SCHEMA_API);
        assertJsonBody(requests.get(0), expectedBody);
    }

    @Test
    void should_fetch_merge_schema_request() {

        mock.raw().on("GET", URL_WATSONX_MERGE_SCHEMA_RESULT_API.formatted(SCHEMA_ID)).replies(
                RawResponse.json(200, RESPONSE.formatted(SCHEMA_ID, PROJECT_ID, Status.COMPLETED.value())),
                RawResponse.json(200, "{}"));

        var response = mergeSchemaService.fetchRequest(SCHEMA_ID);
        assertEquals(Status.COMPLETED.value(), response.entity().results().status());
        assertEquals(SCHEMAS, response.entity().parameters().schemas());

        var parameters = MergeSchemaFetchParameters.builder()
                .spaceId("new-space-id")
                .transactionId("my-transaction-id")
                .build();

        assertNotNull(mergeSchemaService.fetchRequest(SCHEMA_ID, parameters));

        List<CapturedRequest> requests = apiRequests();
        assertThat(requests).hasSize(2);
        assertWatsonxRequest(requests.get(0), URL_WATSONX_MERGE_SCHEMA_RESULT_API.formatted(SCHEMA_ID));
        assertThat(requests.get(0).method()).isEqualTo("GET");
        assertThat(queryParams(requests.get(0))).containsEntry("project_id", PROJECT_ID);
        assertThat(requests.get(0).header("Accept")).isEqualTo("application/json");
        assertWatsonxRequest(requests.get(1), URL_WATSONX_MERGE_SCHEMA_RESULT_API.formatted(SCHEMA_ID));
        assertThat(requests.get(1).method()).isEqualTo("GET");
        assertThat(queryParams(requests.get(1))).containsEntry("space_id", "new-space-id");
        assertThat(requests.get(1).header("Accept")).isEqualTo("application/json");
        assertThat(requests.get(1).header("X-Global-Transaction-Id")).isEqualTo("my-transaction-id");
    }

    @Test
    void should_delete_merge_schema_request() {

        mock.raw().on("DELETE", URL_WATSONX_MERGE_SCHEMA_RESULT_API.formatted(SCHEMA_ID)).replies(
                RawResponse.of(204, Map.of(), null),
                RawResponse.of(204, Map.of(), null));

        assertTrue(mergeSchemaService.deleteRequest(SCHEMA_ID));

        var parameters = MergeSchemaDeleteParameters.builder()
                .spaceId("new-space-id")
                .hardDelete(true)
                .transactionId("my-transaction-id")
                .build();

        assertTrue(mergeSchemaService.deleteRequest(SCHEMA_ID, parameters));

        List<CapturedRequest> requests = apiRequests();
        assertThat(requests).hasSize(2);
        assertWatsonxRequest(requests.get(0), URL_WATSONX_MERGE_SCHEMA_RESULT_API.formatted(SCHEMA_ID));
        assertThat(requests.get(0).method()).isEqualTo("DELETE");
        assertThat(queryParams(requests.get(0))).containsEntry("project_id", PROJECT_ID);
        assertWatsonxRequest(requests.get(1), URL_WATSONX_MERGE_SCHEMA_RESULT_API.formatted(SCHEMA_ID));
        assertThat(requests.get(1).method()).isEqualTo("DELETE");
        assertThat(queryParams(requests.get(1))).containsEntry("space_id", "new-space-id");
        assertThat(queryParams(requests.get(1))).containsEntry("hard_delete", "true");
        assertThat(requests.get(1).header("X-Global-Transaction-Id")).isEqualTo("my-transaction-id");
    }

    @Test
    void should_return_false_when_merge_schema_request_does_not_exist() {

        mock.raw().on("DELETE", URL_WATSONX_MERGE_SCHEMA_RESULT_API.formatted(SCHEMA_ID)).replies(RawResponse.json(404, """
                {
                    "trace": "db2821f494a629c614616e458c85de36",
                    "errors": [
                        {
                            "code": "schema_event_does_not_exist",
                            "message": "Schema request does not exist."
                        }
                    ]
                }"""));

        assertFalse(mergeSchemaService.deleteRequest(SCHEMA_ID));

        List<CapturedRequest> requests = apiRequests();
        assertThat(requests).hasSize(1);
        assertWatsonxRequest(requests.get(0), URL_WATSONX_MERGE_SCHEMA_RESULT_API.formatted(SCHEMA_ID));
        assertThat(requests.get(0).method()).isEqualTo("DELETE");
        assertThat(queryParams(requests.get(0))).containsEntry("project_id", PROJECT_ID);
    }

    @Test
    void should_propagate_error_on_start_merge_schema() {

        mock.raw().on("POST", URL_WATSONX_MERGE_SCHEMA_API).reply(400, """
                {
                    "trace": "db2821f494a629c614616e458c85de36",
                    "errors": [
                        {
                            "code": "invalid_request_entity",
                            "message": "Missing schemas."
                        }
                    ]
                }""");

        var ex = assertThrows(WatsonxException.class, () -> mergeSchemaService.startMergeSchema(SCHEMAS));
        assertEquals(400, ex.statusCode());

        List<CapturedRequest> requests = apiRequests();
        assertThat(requests).hasSize(1);
        assertWatsonxRequest(requests.get(0), URL_WATSONX_MERGE_SCHEMA_API);
    }
}
