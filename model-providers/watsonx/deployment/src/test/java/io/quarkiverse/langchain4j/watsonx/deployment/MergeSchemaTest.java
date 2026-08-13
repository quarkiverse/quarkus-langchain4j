package io.quarkiverse.langchain4j.watsonx.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_MERGE_SCHEMA_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_MERGE_SCHEMA_RESULT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.List;

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

import io.quarkus.test.QuarkusUnitTest;

public class MergeSchemaTest extends WireMockAbstract {

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
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType().orElse(null))
                .response(BEARER_TOKEN, new Date())
                .build();
    }

    @Inject
    MergeSchemaService mergeSchemaService;

    @Test
    void should_start_merge_schema() {

        mockWatsonxBuilder(URL_WATSONX_MERGE_SCHEMA_API, 200)
                .body("""
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
                        }""".formatted(PROJECT_ID))
                .response(RESPONSE.formatted(SCHEMA_ID, PROJECT_ID, Status.SUBMITTED.value()))
                .build();

        var response = mergeSchemaService.startMergeSchema(SCHEMAS);
        assertNotNull(response);
        assertEquals(SCHEMA_ID, response.metadata().id());
        assertEquals(Status.SUBMITTED.value(), response.entity().results().status());
    }

    @Test
    void should_fetch_merge_schema_request() {

        watsonxServer.stubFor(get(urlPathMatching(URL_WATSONX_MERGE_SCHEMA_RESULT_API.formatted(SCHEMA_ID)))
                .withQueryParam("project_id", equalTo(URLEncoder.encode(PROJECT_ID, Charset.defaultCharset())))
                .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                .withHeader("Authorization", equalTo("Bearer %s".formatted(BEARER_TOKEN)))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(RESPONSE.formatted(SCHEMA_ID, PROJECT_ID, Status.COMPLETED.value()))));

        var response = mergeSchemaService.fetchRequest(SCHEMA_ID);
        assertEquals(Status.COMPLETED.value(), response.entity().results().status());
        assertEquals(SCHEMAS, response.entity().parameters().schemas());

        watsonxServer.resetAll();

        watsonxServer.stubFor(get(urlPathMatching(URL_WATSONX_MERGE_SCHEMA_RESULT_API.formatted(SCHEMA_ID)))
                .withQueryParam("space_id", equalTo(URLEncoder.encode("new-space-id", Charset.defaultCharset())))
                .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                .withHeader("Authorization", equalTo("Bearer %s".formatted(BEARER_TOKEN)))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("X-Global-Transaction-Id", equalTo("my-transaction-id"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        var parameters = MergeSchemaFetchParameters.builder()
                .spaceId("new-space-id")
                .transactionId("my-transaction-id")
                .build();

        assertNotNull(mergeSchemaService.fetchRequest(SCHEMA_ID, parameters));
    }

    @Test
    void should_delete_merge_schema_request() {

        watsonxServer.stubFor(delete(urlPathMatching(URL_WATSONX_MERGE_SCHEMA_RESULT_API.formatted(SCHEMA_ID)))
                .withQueryParam("project_id", equalTo(URLEncoder.encode(PROJECT_ID, Charset.defaultCharset())))
                .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                .withHeader("Authorization", equalTo("Bearer %s".formatted(BEARER_TOKEN)))
                .willReturn(aResponse().withStatus(204)));

        assertTrue(mergeSchemaService.deleteRequest(SCHEMA_ID));

        watsonxServer.resetAll();

        watsonxServer.stubFor(delete(urlPathMatching(URL_WATSONX_MERGE_SCHEMA_RESULT_API.formatted(SCHEMA_ID)))
                .withQueryParam("space_id", equalTo(URLEncoder.encode("new-space-id", Charset.defaultCharset())))
                .withQueryParam("hard_delete", equalTo("true"))
                .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                .withHeader("Authorization", equalTo("Bearer %s".formatted(BEARER_TOKEN)))
                .withHeader("X-Global-Transaction-Id", equalTo("my-transaction-id"))
                .willReturn(aResponse().withStatus(204)));

        var parameters = MergeSchemaDeleteParameters.builder()
                .spaceId("new-space-id")
                .hardDelete(true)
                .transactionId("my-transaction-id")
                .build();

        assertTrue(mergeSchemaService.deleteRequest(SCHEMA_ID, parameters));
    }

    @Test
    void should_return_false_when_merge_schema_request_does_not_exist() {

        watsonxServer.stubFor(delete(urlPathMatching(URL_WATSONX_MERGE_SCHEMA_RESULT_API.formatted(SCHEMA_ID)))
                .withQueryParam("project_id", equalTo(URLEncoder.encode(PROJECT_ID, Charset.defaultCharset())))
                .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                .withHeader("Authorization", equalTo("Bearer %s".formatted(BEARER_TOKEN)))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "trace": "db2821f494a629c614616e458c85de36",
                                    "errors": [
                                        {
                                            "code": "schema_event_does_not_exist",
                                            "message": "Schema request does not exist."
                                        }
                                    ]
                                }""")));

        assertFalse(mergeSchemaService.deleteRequest(SCHEMA_ID));
    }

    @Test
    void should_propagate_error_on_start_merge_schema() {

        mockWatsonxBuilder(URL_WATSONX_MERGE_SCHEMA_API, 400)
                .response("""
                        {
                            "trace": "db2821f494a629c614616e458c85de36",
                            "errors": [
                                {
                                    "code": "invalid_request_entity",
                                    "message": "Missing schemas."
                                }
                            ]
                        }""")
                .build();

        var ex = assertThrows(WatsonxException.class, () -> mergeSchemaService.startMergeSchema(SCHEMAS));
        assertEquals(400, ex.statusCode());
    }
}
