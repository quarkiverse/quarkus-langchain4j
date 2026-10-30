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
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CLUSTER_SCHEMA_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CLUSTER_SCHEMA_RESULT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.core.auth.ibmcloud.IBMCloudAuthenticator;
import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.textprocessing.Schema;
import com.ibm.watsonx.ai.textprocessing.Status;
import com.ibm.watsonx.ai.textprocessing.schema.cluster.ClusterSchemaDeleteParameters;
import com.ibm.watsonx.ai.textprocessing.schema.cluster.ClusterSchemaFetchParameters;
import com.ibm.watsonx.ai.textprocessing.schema.cluster.ClusterSchemaService;
import com.ibm.watsonx.ai.textprocessing.schema.cluster.ClusterSchemas;

import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkus.test.QuarkusUnitTest;

public class ClusterSchemaTest extends WireMockAbstract {

    static String SCHEMA_ID = "my-id";

    static List<ClusterSchemas> SCHEMAS = List.of(
            new ClusterSchemas("invoice.pdf",
                    Schema.builder().documentType("Invoice").documentDescription("An invoice").build()),
            new ClusterSchemas("receipt.pdf",
                    Schema.builder().documentType("Receipt").documentDescription("A receipt").build()));

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
                                "document_name": "invoice.pdf",
                                "schema": {
                                    "document_type": "Invoice",
                                    "document_description": "An invoice"
                                }
                            },
                            {
                                "document_name": "receipt.pdf",
                                "schema": {
                                    "document_type": "Receipt",
                                    "document_description": "A receipt"
                                }
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
    ClusterSchemaService clusterSchemaService;

    @Test
    void should_start_cluster_schema() {

        mockWatsonxBuilder(URL_WATSONX_CLUSTER_SCHEMA_API, 200)
                .body("""
                        {
                            "project_id": "%s",
                            "parameters": {
                                "schemas": [
                                    {
                                        "document_name": "invoice.pdf",
                                        "schema": {
                                            "document_type": "Invoice",
                                            "document_description": "An invoice"
                                        }
                                    },
                                    {
                                        "document_name": "receipt.pdf",
                                        "schema": {
                                            "document_type": "Receipt",
                                            "document_description": "A receipt"
                                        }
                                    }
                                ]
                            }
                        }""".formatted(PROJECT_ID))
                .response(RESPONSE.formatted(SCHEMA_ID, PROJECT_ID, Status.SUBMITTED.value()))
                .build();

        var response = clusterSchemaService.startClusterSchema(SCHEMAS);
        assertNotNull(response);
        assertEquals(SCHEMA_ID, response.metadata().id());
        assertEquals(Status.SUBMITTED.value(), response.entity().results().status());
    }

    @Test
    void should_fetch_cluster_schema_request() {

        watsonxServer.stubFor(get(urlPathMatching(URL_WATSONX_CLUSTER_SCHEMA_RESULT_API.formatted(SCHEMA_ID)))
                .withQueryParam("project_id", equalTo(URLEncoder.encode(PROJECT_ID, Charset.defaultCharset())))
                .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                .withHeader("Authorization", equalTo("Bearer %s".formatted(BEARER_TOKEN)))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(RESPONSE.formatted(SCHEMA_ID, PROJECT_ID, Status.COMPLETED.value()))));

        var response = clusterSchemaService.fetchRequest(SCHEMA_ID);
        assertEquals(Status.COMPLETED.value(), response.entity().results().status());
        assertEquals(SCHEMAS, response.entity().parameters().schemas());

        watsonxServer.resetAll();

        watsonxServer.stubFor(get(urlPathMatching(URL_WATSONX_CLUSTER_SCHEMA_RESULT_API.formatted(SCHEMA_ID)))
                .withQueryParam("space_id", equalTo(URLEncoder.encode("new-space-id", Charset.defaultCharset())))
                .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                .withHeader("Authorization", equalTo("Bearer %s".formatted(BEARER_TOKEN)))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("X-Global-Transaction-Id", equalTo("my-transaction-id"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        var parameters = ClusterSchemaFetchParameters.builder()
                .spaceId("new-space-id")
                .transactionId("my-transaction-id")
                .build();

        assertNotNull(clusterSchemaService.fetchRequest(SCHEMA_ID, parameters));
    }

    @Test
    void should_delete_cluster_schema_request() {

        watsonxServer.stubFor(delete(urlPathMatching(URL_WATSONX_CLUSTER_SCHEMA_RESULT_API.formatted(SCHEMA_ID)))
                .withQueryParam("project_id", equalTo(URLEncoder.encode(PROJECT_ID, Charset.defaultCharset())))
                .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                .withHeader("Authorization", equalTo("Bearer %s".formatted(BEARER_TOKEN)))
                .willReturn(aResponse().withStatus(204)));

        assertTrue(clusterSchemaService.deleteRequest(SCHEMA_ID));

        watsonxServer.resetAll();

        watsonxServer.stubFor(delete(urlPathMatching(URL_WATSONX_CLUSTER_SCHEMA_RESULT_API.formatted(SCHEMA_ID)))
                .withQueryParam("space_id", equalTo(URLEncoder.encode("new-space-id", Charset.defaultCharset())))
                .withQueryParam("hard_delete", equalTo("true"))
                .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                .withHeader("Authorization", equalTo("Bearer %s".formatted(BEARER_TOKEN)))
                .withHeader("X-Global-Transaction-Id", equalTo("my-transaction-id"))
                .willReturn(aResponse().withStatus(204)));

        var parameters = ClusterSchemaDeleteParameters.builder()
                .spaceId("new-space-id")
                .hardDelete(true)
                .transactionId("my-transaction-id")
                .build();

        assertTrue(clusterSchemaService.deleteRequest(SCHEMA_ID, parameters));
    }

    @Test
    void should_return_false_when_cluster_schema_request_does_not_exist() {

        watsonxServer.stubFor(delete(urlPathMatching(URL_WATSONX_CLUSTER_SCHEMA_RESULT_API.formatted(SCHEMA_ID)))
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

        assertFalse(clusterSchemaService.deleteRequest(SCHEMA_ID));
    }

    @Test
    void should_propagate_error_on_start_cluster_schema() {

        mockWatsonxBuilder(URL_WATSONX_CLUSTER_SCHEMA_API, 400)
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

        var ex = assertThrows(WatsonxException.class, () -> clusterSchemaService.startClusterSchema(SCHEMAS));
        assertEquals(400, ex.statusCode());
    }

    @Test
    void should_propagate_error_on_delete_cluster_schema_request() {

        watsonxServer.stubFor(delete(urlPathMatching(URL_WATSONX_CLUSTER_SCHEMA_RESULT_API.formatted(SCHEMA_ID)))
                .withQueryParam("project_id", equalTo(URLEncoder.encode(PROJECT_ID, Charset.defaultCharset())))
                .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                .withHeader("Authorization", equalTo("Bearer %s".formatted(BEARER_TOKEN)))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "trace": "db2821f494a629c614616e458c85de36",
                                    "errors": [
                                        {
                                            "code": "invalid_request_entity",
                                            "message": "Invalid identifier."
                                        }
                                    ]
                                }""")));

        var ex = assertThrows(WatsonxException.class, () -> clusterSchemaService.deleteRequest(SCHEMA_ID));
        assertEquals(400, ex.statusCode());
    }

    @Test
    void should_enable_the_client_logger() {

        mockWatsonxBuilder(URL_WATSONX_CLUSTER_SCHEMA_API, 200)
                .response(RESPONSE.formatted(SCHEMA_ID, PROJECT_ID, Status.SUBMITTED.value()))
                .build();

        assertNotNull(clusterSchemaService(true, false).startClusterSchema(SCHEMAS));
        assertNotNull(clusterSchemaService(false, true).startClusterSchema(SCHEMAS));

        QuarkusRestClientConfig.setLogCurl(true);
        try {
            assertNotNull(clusterSchemaService(false, false).startClusterSchema(SCHEMAS));
        } finally {
            QuarkusRestClientConfig.clear();
        }
    }

    @Test
    void should_throw_error_when_the_base_url_is_not_valid() {

        var ex = assertThrows(RuntimeException.class, () -> ClusterSchemaService.builder()
                .apiKey(API_KEY)
                .baseUrl("unknown-protocol://localhost")
                .projectId(PROJECT_ID)
                .build());

        assertInstanceOf(MalformedURLException.class, ex.getCause());
    }

    private ClusterSchemaService clusterSchemaService(boolean logRequests, boolean logResponses) {
        return ClusterSchemaService.builder()
                .baseUrl(URL_WATSONX_SERVER)
                .projectId(PROJECT_ID)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .authenticator(IBMCloudAuthenticator.builder()
                        .baseUrl(URI.create(URL_IAM_SERVER))
                        .apiKey(API_KEY)
                        .build())
                .build();
    }
}
