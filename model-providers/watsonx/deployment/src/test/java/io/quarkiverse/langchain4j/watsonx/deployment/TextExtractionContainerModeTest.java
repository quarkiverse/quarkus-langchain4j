package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.COS_PATH;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_TEXT_EXTRACTION_START_API;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.function.Supplier;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationService;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionResponse;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionService;

import io.quarkus.test.QuarkusExtensionTest;

public class TextExtractionContainerModeTest extends WatsonxMockTestBase {

    static final String FILE_NAME = "test.pdf";
    static final String PROCESS_EXTRACTION_ID = "my-id";
    static final String RESOLVED_BUCKET_NAME = "project-default-bucket";
    static final String PROJECT_PATH = "/projects/v2/projects/" + PROJECT_ID;

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-base-url",
                    "${quarkus.langchain4j.mock-api.url}/projects")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .setArchiveProducer(new Supplier<JavaArchive>() {
                @Override
                public JavaArchive get() {
                    return ShrinkWrap.create(JavaArchive.class)
                            .addClass(WatsonxTestUtil.class)
                            .addAsResource(FILE_NAME);
                }
            });

    @Inject
    @ConfigProperty(name = "quarkus.langchain4j.mock-api.url")
    String mockUrl;

    @BeforeEach
    void projectStorage() {
        mock.raw().on("GET", PROJECT_PATH).reply(200, """
                {
                  "metadata": {
                    "guid": "%s",
                    "url": "/v2/projects/%s",
                    "created_at": "2026-01-01T00:00:00.000Z",
                    "updated_at": "2026-01-01T00:00:00.000Z"
                  },
                  "entity": {
                    "name": "test-project",
                    "storage": {
                      "type": "bmcos_object_storage",
                      "guid": "storage-guid",
                      "properties": {
                        "bucket_name": "%s",
                        "endpoint_url": "%s"
                      }
                    }
                  }
                }
                """.formatted(PROJECT_ID, PROJECT_ID, RESOLVED_BUCKET_NAME, mockUrl + COS_PATH));

    }

    @Inject
    TextExtractionService textExtraction;

    @Inject
    TextClassificationService classification;

    @Test
    void uploadAndStartExtraction_usesProjectDefaultContainer() throws Exception {
        var file = Path.of(ClassLoader.getSystemResource(FILE_NAME).toURI()).toFile();

        mock.raw().on("PUT", cosFile()).reply(200, "");

        mock.raw().on("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)
                .reply(200, """
                        {
                          "metadata": {
                            "id": "%s",
                            "created_at": "2026-01-01T00:00:00.000Z",
                            "project_id": "%s",
                            "name": "extract"
                          },
                          "entity": {
                            "results": {
                              "status": "submitted",
                              "number_pages_processed": 0
                            }
                          }
                        }
                        """.formatted(PROCESS_EXTRACTION_ID, PROJECT_ID));

        TextExtractionResponse response = textExtraction.uploadAndStartExtraction(file);

        assertNotNull(response);
        assertEquals(PROCESS_EXTRACTION_ID, response.metadata().id());
        assertEquals("submitted", response.entity().results().status());

        assertProjectAndStorageRequests();
        assertThat(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API)).hasSize(1);
        assertWatsonxRequest(requests("POST", URL_WATSONX_TEXT_EXTRACTION_START_API).get(0),
                URL_WATSONX_TEXT_EXTRACTION_START_API);
    }

    @Test
    void classificationUploadUsesSeparateProjectEndpoint() throws Exception {
        var file = Path.of(ClassLoader.getSystemResource(FILE_NAME).toURI()).toFile();
        mock.raw().on("PUT", cosFile()).reply(200, "");

        assertThat(classification.uploadFile(file)).isTrue();

        assertProjectAndStorageRequests();
    }

    private void assertProjectAndStorageRequests() {
        assertThat(requests("GET", PROJECT_PATH)).hasSize(1);
        assertThat(requests("GET", "/watsonx/v2/projects/" + PROJECT_ID)).isEmpty();
        assertThat(requests("GET", PROJECT_PATH).get(0).header("Authorization")).isEqualTo("Bearer " + MOCK_TOKEN);
        assertThat(requests("PUT", cosFile())).hasSize(1);
        assertThat(requests("PUT", cosFile()).get(0).header("Authorization")).isEqualTo("Bearer " + MOCK_TOKEN);
    }

    private static String cosFile() {
        return COS_PATH + "/" + RESOLVED_BUCKET_NAME + "/" + FILE_NAME;
    }

}
