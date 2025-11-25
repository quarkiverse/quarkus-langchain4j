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
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_TEXT_CLASSIFICATION_START_API;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.ibm.watsonx.ai.textprocessing.Status;
import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationDeleteParameters;
import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationFetchParameters;
import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationService;

import io.quarkus.test.QuarkusUnitTest;

public class TextClassificationTest extends WireMockAbstract {

    static String CONNECTION_ID = "connection_id";
    static String BUCKET_NAME = "my-bucket";
    static String FILE_NAME = "test.pdf";
    static String PROCESS_CLASSIFICATION_ID = "my-id";

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.cos-url", URL_COS_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.document-reference.connection",
                    CONNECTION_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.document-reference.bucket-name",
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
    TextClassificationService classificationService;

    @Test
    void should_start_classification() throws Exception {

        var RESULT = Files.readString(Path.of(ClassLoader.getSystemResource("classification_response.json").toURI()));

        mockWatsonxBuilder(URL_WATSONX_TEXT_CLASSIFICATION_START_API, 200)
                .body("""
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
                        }""".formatted(PROJECT_ID))
                .response(RESULT)
                .build();

        var result = classificationService.startClassification("test.pdf");
        assertNotNull(result);
    }

    @Test
    void should_fetch_classification_request() throws Exception {

        var JOB = Files.readString(Path.of(ClassLoader.getSystemResource("classification_job.json").toURI()));

        watsonxServer.stubFor(get(urlPathMatching("/ml/v1/text/classifications/id"))
                .withQueryParam("project_id", equalTo(URLEncoder.encode(PROJECT_ID, Charset.defaultCharset())))
                .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                .withHeader("Authorization", equalTo("Bearer my_super_token"))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(JOB.formatted(Status.SUBMITTED.value()))));

        var response = classificationService.fetchClassificationRequest("id");
        assertEquals(response.entity().results().status(), Status.SUBMITTED.value());

        var projectId = URLEncoder.encode("new-project-id", Charset.defaultCharset());

        watsonxServer.resetAll();

        watsonxServer
                .stubFor(get(urlPathMatching("/ml/v1/text/classifications/id"))
                        .withQueryParam("project_id", equalTo(URLEncoder.encode(projectId, Charset.defaultCharset())))
                        .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                        .withHeader("Authorization", equalTo("Bearer my_super_token"))
                        .withHeader("Accept", equalTo("application/json"))
                        .withHeader("X-Global-Transaction-Id", equalTo("my-transaction-id"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{}")));

        var parameters = TextClassificationFetchParameters.builder()
                .projectId("new-project-id")
                .transactionId("my-transaction-id")
                .build();

        response = classificationService.fetchClassificationRequest("id", parameters);
        assertNotNull(response);

        var spaceId = URLEncoder.encode("new-space-id", Charset.defaultCharset());

        watsonxServer.resetAll();

        watsonxServer
                .stubFor(get(urlPathMatching("/ml/v1/text/classifications/id"))
                        .withQueryParam("space_id", equalTo(URLEncoder.encode(spaceId, Charset.defaultCharset())))
                        .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                        .withHeader("Authorization", equalTo("Bearer my_super_token"))
                        .withHeader("Accept", equalTo("application/json"))
                        .withHeader("X-Global-Transaction-Id", equalTo("my-transaction-id"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{}")));

        parameters = TextClassificationFetchParameters.builder()
                .spaceId("new-space-id")
                .transactionId("my-transaction-id")
                .build();

        response = classificationService.fetchClassificationRequest("id", parameters);
        assertNotNull(response);
    }

    @Test
    void should_delete_classification_request() {

        watsonxServer
                .stubFor(delete(urlPathMatching("/ml/v1/text/classifications/id"))
                        .withQueryParam("project_id", equalTo(URLEncoder.encode(PROJECT_ID, Charset.defaultCharset())))
                        .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                        .withHeader("Authorization", equalTo("Bearer my_super_token"))
                        .willReturn(aResponse()
                                .withStatus(204)));

        assertTrue(classificationService.deleteRequest("id"));

        watsonxServer
                .stubFor(delete(urlPathMatching("/ml/v1/text/classifications/id"))
                        .withQueryParam("project_id", equalTo(URLEncoder.encode(PROJECT_ID, Charset.defaultCharset())))
                        .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                        .withHeader("Authorization", equalTo("Bearer my_super_token"))
                        .willReturn(aResponse()
                                .withStatus(404)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                            "trace": "db2821f494a629c614616e458c85de36",
                                            "errors": [
                                                {
                                                    "code": "text_classification_event_does_not_exist",
                                                    "message": "Text classification request does not exist."
                                                }
                                            ]
                                        }""")));

        assertFalse(classificationService.deleteRequest("id"));

        var projectId = URLEncoder.encode("new-project-id", Charset.defaultCharset());

        watsonxServer.resetAll();

        watsonxServer
                .stubFor(
                        delete(urlPathMatching("/ml/v1/text/classifications/id"))
                                .withQueryParam("project_id", equalTo(URLEncoder.encode(projectId, Charset.defaultCharset())))
                                .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                                .withHeader("Authorization", equalTo("Bearer my_super_token"))
                                .withHeader("X-Global-Transaction-Id", equalTo("my-transaction-id"))
                                .willReturn(aResponse()
                                        .withStatus(204)));

        var parameters = TextClassificationDeleteParameters.builder()
                .projectId("new-project-id")
                .hardDelete(true)
                .transactionId("my-transaction-id")
                .build();

        assertTrue(classificationService.deleteRequest("id", parameters));

        var spaceId = URLEncoder.encode("new-space-id", Charset.defaultCharset());

        watsonxServer
                .stubFor(delete(urlPathMatching("/ml/v1/text/classifications/id"))
                        .withQueryParam("space_id", equalTo(URLEncoder.encode(spaceId, Charset.defaultCharset())))
                        .withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"))
                        .withHeader("Authorization", equalTo("Bearer my_super_token"))
                        .willReturn(aResponse()
                                .withStatus(204)));

        parameters = TextClassificationDeleteParameters.builder()
                .spaceId("new-space-id")
                .build();

        assertTrue(classificationService.deleteRequest("id", parameters));
    }

    @Test
    void should_delete_file() throws Exception {

        cosServer.resetAll();

        cosServer.stubFor(delete("/%s/%s".formatted("my-bucket", "test.pdf"))
                .withHeader("Authorization", equalTo("Bearer my_super_token"))
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
                                    <Resource>/andreaproject-donotdelete-pr-xnran4g4ptd1wo/ciao.pdf</Resource>
                                    <RequestId>df887c2b-43c3-4933-a3a1-b0e19e7c2231</RequestId>
                                    <httpStatusCode>403</httpStatusCode>
                                </Error>""")));

        cosServer.stubFor(delete("/%s/%s".formatted("my-bucket", "test.pdf"))
                .withHeader("Authorization", equalTo("Bearer my_super_token"))
                .inScenario("retry")
                .whenScenarioStateIs("retry")
                .willSetStateTo(Scenario.STARTED)
                .willReturn(aResponse().withStatus(204)));

        assertTrue(classificationService.deleteFile("my-bucket", "test.pdf"));
        Thread.sleep(500);
        cosServer.verify(2, deleteRequestedFor(urlEqualTo("/%s/%s".formatted("my-bucket", "test.pdf"))));
    }

    @Test
    void should_upload_file() throws Exception {

        var file = new File(ClassLoader.getSystemResource("test.pdf").toURI());
        cosServer.stubFor(put("/%s/%s".formatted("my-bucket", "test.pdf"))
                .withHeader("Authorization", equalTo("Bearer my_super_token"))
                .willReturn(aResponse().withStatus(200)));

        assertTrue(classificationService.uploadFile(file));
    }
}
