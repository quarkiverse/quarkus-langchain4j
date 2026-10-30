package io.quarkiverse.langchain4j.watsonx.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_COS_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CREATE_SCHEMA_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CREATE_SCHEMA_RESULT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Date;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.textprocessing.Status;
import com.ibm.watsonx.ai.textprocessing.schema.create.CreateSchemaDeleteParameters;
import com.ibm.watsonx.ai.textprocessing.schema.create.CreateSchemaFetchParameters;
import com.ibm.watsonx.ai.textprocessing.schema.create.CreateSchemaService;

import io.quarkus.test.QuarkusUnitTest;

public class CreateSchemaTest extends WireMockAbstract {

    static String CONNECTION_ID = "connection_id";
    static String BUCKET_NAME = "my-bucket";
    static String FILE_NAME = "test.pdf";
    static String SCHEMA_ID = "my-id";

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
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.schema.create.cos-url", URL_COS_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.schema.create.document-reference.connection",
                    CONNECTION_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.schema.create.document-reference.bucket-name",
                    BUCKET_NAME)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(WireMockUtil.class)
                    .addAsResource(FILE_NAME));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType().orElse(null))
                .response(BEARER_TOKEN, new Date())
                .build();
    }

    @Inject
    CreateSchemaService createSchemaService;

    @Test
    void should_start_create_schema() {

        mockWatsonxBuilder(URL_WATSONX_CREATE_SCHEMA_API, 200)
                .body("""
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
                        }""".formatted(PROJECT_ID, CONNECTION_ID, FILE_NAME, BUCKET_NAME))
                .response(RESPONSE.formatted(SCHEMA_ID, PROJECT_ID, CONNECTION_ID, FILE_NAME, BUCKET_NAME,
                        Status.SUBMITTED.value()))
                .build();

        var response = createSchemaService.startCreateSchema(FILE_NAME);
        assertNotNull(response);
        assertEquals(SCHEMA_ID, response.metadata().id());
        assertEquals(Status.SUBMITTED.value(), response.entity().results().status());
    }

    @Test
    void should_fetch_create_schema_request() {

        watsonxServer.stubFor(get(urlPathMatching(URL_WATSONX_CREATE_SCHEMA_RESULT_API.formatted(SCHEMA_ID)))
                .withQueryParam("project_id", equalTo(URLEncoder.encode(PROJECT_ID, Charset.defaultCharset())))
                .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                .withHeader("Authorization", equalTo("Bearer %s".formatted(BEARER_TOKEN)))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(RESPONSE.formatted(SCHEMA_ID, PROJECT_ID, CONNECTION_ID, FILE_NAME, BUCKET_NAME,
                                Status.COMPLETED.value()))));

        var response = createSchemaService.fetchRequest(SCHEMA_ID);
        assertEquals(Status.COMPLETED.value(), response.entity().results().status());

        watsonxServer.resetAll();

        watsonxServer.stubFor(get(urlPathMatching(URL_WATSONX_CREATE_SCHEMA_RESULT_API.formatted(SCHEMA_ID)))
                .withQueryParam("space_id", equalTo(URLEncoder.encode("new-space-id", Charset.defaultCharset())))
                .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                .withHeader("Authorization", equalTo("Bearer %s".formatted(BEARER_TOKEN)))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("X-Global-Transaction-Id", equalTo("my-transaction-id"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        var parameters = CreateSchemaFetchParameters.builder()
                .spaceId("new-space-id")
                .transactionId("my-transaction-id")
                .build();

        assertNotNull(createSchemaService.fetchRequest(SCHEMA_ID, parameters));
    }

    @Test
    void should_delete_create_schema_request() {

        watsonxServer.stubFor(delete(urlPathMatching(URL_WATSONX_CREATE_SCHEMA_RESULT_API.formatted(SCHEMA_ID)))
                .withQueryParam("project_id", equalTo(URLEncoder.encode(PROJECT_ID, Charset.defaultCharset())))
                .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                .withHeader("Authorization", equalTo("Bearer %s".formatted(BEARER_TOKEN)))
                .willReturn(aResponse().withStatus(204)));

        assertTrue(createSchemaService.deleteRequest(SCHEMA_ID));

        watsonxServer.resetAll();

        watsonxServer.stubFor(delete(urlPathMatching(URL_WATSONX_CREATE_SCHEMA_RESULT_API.formatted(SCHEMA_ID)))
                .withQueryParam("space_id", equalTo(URLEncoder.encode("new-space-id", Charset.defaultCharset())))
                .withQueryParam("hard_delete", equalTo("true"))
                .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                .withHeader("Authorization", equalTo("Bearer %s".formatted(BEARER_TOKEN)))
                .withHeader("X-Global-Transaction-Id", equalTo("my-transaction-id"))
                .willReturn(aResponse().withStatus(204)));

        var parameters = CreateSchemaDeleteParameters.builder()
                .spaceId("new-space-id")
                .hardDelete(true)
                .transactionId("my-transaction-id")
                .build();

        assertTrue(createSchemaService.deleteRequest(SCHEMA_ID, parameters));
    }

    @Test
    void should_return_false_when_create_schema_request_does_not_exist() {

        watsonxServer.stubFor(delete(urlPathMatching(URL_WATSONX_CREATE_SCHEMA_RESULT_API.formatted(SCHEMA_ID)))
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

        assertFalse(createSchemaService.deleteRequest(SCHEMA_ID));
    }

    @Test
    void should_propagate_error_on_start_create_schema() {

        mockWatsonxBuilder(URL_WATSONX_CREATE_SCHEMA_API, 400)
                .response("""
                        {
                            "trace": "db2821f494a629c614616e458c85de36",
                            "errors": [
                                {
                                    "code": "invalid_request_entity",
                                    "message": "Missing document reference."
                                }
                            ]
                        }""")
                .build();

        var ex = assertThrows(WatsonxException.class, () -> createSchemaService.startCreateSchema(FILE_NAME));
        assertEquals(400, ex.statusCode());
    }

    @Test
    void should_upload_file() throws Exception {

        var file = new File(ClassLoader.getSystemResource(FILE_NAME).toURI());
        cosServer.stubFor(put("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))
                .withHeader("Authorization", equalTo("Bearer %s".formatted(BEARER_TOKEN)))
                .willReturn(aResponse().withStatus(200)));

        assertTrue(createSchemaService.uploadFile(file));
    }

    @Test
    void should_delete_file() throws Exception {

        cosServer.stubFor(delete("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))
                .withHeader("Authorization", equalTo("Bearer %s".formatted(BEARER_TOKEN)))
                .inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("retry")
                .willReturn(aResponse()
                        .withStatus(403)
                        .withHeader("Content-Type", "application/xml")
                        .withBody("""
                                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                                <Error>
                                    <Code>AccessDenied</Code>
                                    <Message>Access Denied</Message>
                                    <httpStatusCode>403</httpStatusCode>
                                </Error>""")));

        cosServer.stubFor(delete("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))
                .withHeader("Authorization", equalTo("Bearer %s".formatted(BEARER_TOKEN)))
                .inScenario("retry")
                .whenScenarioStateIs("retry")
                .willSetStateTo(Scenario.STARTED)
                .willReturn(aResponse().withStatus(204)));

        assertTrue(createSchemaService.deleteFile(BUCKET_NAME, FILE_NAME));
        cosServer.verify(2, deleteRequestedFor(urlEqualTo("/%s/%s".formatted(BUCKET_NAME, FILE_NAME))));
    }
}
