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
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_IMPROVE_SCHEMA_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_IMPROVE_SCHEMA_RESULT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Date;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.textprocessing.Schema;
import com.ibm.watsonx.ai.textprocessing.Status;
import com.ibm.watsonx.ai.textprocessing.schema.improve.ImproveSchemaDeleteParameters;
import com.ibm.watsonx.ai.textprocessing.schema.improve.ImproveSchemaFetchParameters;
import com.ibm.watsonx.ai.textprocessing.schema.improve.ImproveSchemaService;

import io.quarkus.test.QuarkusUnitTest;

public class ImproveSchemaTest extends WireMockAbstract {

    static String SCHEMA_ID = "my-id";
    static String DOCUMENT_TYPE = "Invoice";
    static String DOCUMENT_DESCRIPTION = "An invoice issued to a customer";

    static Schema SCHEMA = Schema.builder()
            .documentType(DOCUMENT_TYPE)
            .documentDescription(DOCUMENT_DESCRIPTION)
            .build();

    static String RESPONSE = """
            {
                "metadata": {
                    "id": "%s",
                    "created_at": "2023-05-02T16:27:51Z",
                    "project_id": "%s"
                },
                "entity": {
                    "parameters": {
                        "schema": {
                            "document_type": "%s",
                            "document_description": "%s"
                        }
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
    ImproveSchemaService improveSchemaService;

    @Test
    void should_start_improve_schema() {

        mockWatsonxBuilder(URL_WATSONX_IMPROVE_SCHEMA_API, 200)
                .body("""
                        {
                            "project_id": "%s",
                            "parameters": {
                                "schema": {
                                    "document_type": "%s",
                                    "document_description": "%s"
                                }
                            }
                        }""".formatted(PROJECT_ID, DOCUMENT_TYPE, DOCUMENT_DESCRIPTION))
                .response(RESPONSE.formatted(SCHEMA_ID, PROJECT_ID, DOCUMENT_TYPE, DOCUMENT_DESCRIPTION,
                        Status.SUBMITTED.value()))
                .build();

        var response = improveSchemaService.startImproveSchema(SCHEMA);
        assertNotNull(response);
        assertEquals(SCHEMA_ID, response.metadata().id());
        assertEquals(Status.SUBMITTED.value(), response.entity().results().status());
    }

    @Test
    void should_fetch_improve_schema_request() {

        watsonxServer.stubFor(get(urlPathMatching(URL_WATSONX_IMPROVE_SCHEMA_RESULT_API.formatted(SCHEMA_ID)))
                .withQueryParam("project_id", equalTo(URLEncoder.encode(PROJECT_ID, Charset.defaultCharset())))
                .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                .withHeader("Authorization", equalTo("Bearer %s".formatted(BEARER_TOKEN)))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(RESPONSE.formatted(SCHEMA_ID, PROJECT_ID, DOCUMENT_TYPE, DOCUMENT_DESCRIPTION,
                                Status.COMPLETED.value()))));

        var response = improveSchemaService.fetchRequest(SCHEMA_ID);
        assertEquals(Status.COMPLETED.value(), response.entity().results().status());
        assertEquals(SCHEMA, response.entity().parameters().schema());

        watsonxServer.resetAll();

        watsonxServer.stubFor(get(urlPathMatching(URL_WATSONX_IMPROVE_SCHEMA_RESULT_API.formatted(SCHEMA_ID)))
                .withQueryParam("space_id", equalTo(URLEncoder.encode("new-space-id", Charset.defaultCharset())))
                .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                .withHeader("Authorization", equalTo("Bearer %s".formatted(BEARER_TOKEN)))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("X-Global-Transaction-Id", equalTo("my-transaction-id"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        var parameters = ImproveSchemaFetchParameters.builder()
                .spaceId("new-space-id")
                .transactionId("my-transaction-id")
                .build();

        assertNotNull(improveSchemaService.fetchRequest(SCHEMA_ID, parameters));
    }

    @Test
    void should_delete_improve_schema_request() {

        watsonxServer.stubFor(delete(urlPathMatching(URL_WATSONX_IMPROVE_SCHEMA_RESULT_API.formatted(SCHEMA_ID)))
                .withQueryParam("project_id", equalTo(URLEncoder.encode(PROJECT_ID, Charset.defaultCharset())))
                .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                .withHeader("Authorization", equalTo("Bearer %s".formatted(BEARER_TOKEN)))
                .willReturn(aResponse().withStatus(204)));

        assertTrue(improveSchemaService.deleteRequest(SCHEMA_ID));

        watsonxServer.resetAll();

        watsonxServer.stubFor(delete(urlPathMatching(URL_WATSONX_IMPROVE_SCHEMA_RESULT_API.formatted(SCHEMA_ID)))
                .withQueryParam("space_id", equalTo(URLEncoder.encode("new-space-id", Charset.defaultCharset())))
                .withQueryParam("hard_delete", equalTo("true"))
                .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                .withHeader("Authorization", equalTo("Bearer %s".formatted(BEARER_TOKEN)))
                .withHeader("X-Global-Transaction-Id", equalTo("my-transaction-id"))
                .willReturn(aResponse().withStatus(204)));

        var parameters = ImproveSchemaDeleteParameters.builder()
                .spaceId("new-space-id")
                .hardDelete(true)
                .transactionId("my-transaction-id")
                .build();

        assertTrue(improveSchemaService.deleteRequest(SCHEMA_ID, parameters));
    }

    @Test
    void should_return_false_when_improve_schema_request_does_not_exist() {

        watsonxServer.stubFor(delete(urlPathMatching(URL_WATSONX_IMPROVE_SCHEMA_RESULT_API.formatted(SCHEMA_ID)))
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

        assertFalse(improveSchemaService.deleteRequest(SCHEMA_ID));
    }

    @Test
    void should_propagate_error_on_start_improve_schema() {

        mockWatsonxBuilder(URL_WATSONX_IMPROVE_SCHEMA_API, 400)
                .response("""
                        {
                            "trace": "db2821f494a629c614616e458c85de36",
                            "errors": [
                                {
                                    "code": "invalid_request_entity",
                                    "message": "Missing schema."
                                }
                            ]
                        }""")
                .build();

        var ex = assertThrows(WatsonxException.class, () -> improveSchemaService.startImproveSchema(SCHEMA));
        assertEquals(400, ex.statusCode());
    }
}
