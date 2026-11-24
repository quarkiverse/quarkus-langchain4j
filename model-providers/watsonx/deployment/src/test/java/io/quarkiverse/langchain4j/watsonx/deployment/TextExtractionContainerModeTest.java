package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.COS_PATH;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_TEXT_EXTRACTION_START_API;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.core.auth.Authenticator;
import com.ibm.watsonx.ai.project.ProjectService;
import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationService;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionResponse;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionService;

import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.QuarkusExtensionTest;

public class TextExtractionContainerModeTest extends WatsonxMockTestBase {

    static final String FILE_NAME = "test.pdf";
    static final String PROCESS_EXTRACTION_ID = "my-id";
    static final String RESOLVED_BUCKET_NAME = "project-default-bucket";
    static final String PROJECT_PATH = "/projects/v2/projects/" + PROJECT_ID;
    private static final String CUSTOM_PROJECT_PATH = "/custom-projects/v2/projects/" + PROJECT_ID;
    private static final String CUSTOM_TOKEN = "custom-storage-token";
    private static final String PROJECT_TOKEN = "custom-project-token";

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-base-url",
                    "${quarkus.langchain4j.mock-api.url}/projects")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url",
                    "${quarkus.langchain4j.mock-api.url}/watsonx")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom-auth.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom-auth.iam.base-url",
                    "${quarkus.langchain4j.mock-api.url}/watsonx")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom-project.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom-project.iam.base-url",
                    "${quarkus.langchain4j.mock-api.url}/watsonx")
            .setArchiveProducer(new Supplier<JavaArchive>() {
                @Override
                public JavaArchive get() {
                    return ShrinkWrap.create(JavaArchive.class)
                            .addClasses(WatsonxTestUtil.class, FixedAuthenticator.class,
                                    ExtractionAuthenticatorCustomizer.class, ClassificationAuthenticatorCustomizer.class,
                                    ExtractionProjectServiceCustomizer.class, ClassificationProjectServiceCustomizer.class)
                            .addAsResource(FILE_NAME);
                }
            });

    @Inject
    @ConfigProperty(name = "quarkus.langchain4j.mock-api.url")
    String mockUrl;

    @BeforeEach
    void projectStorage() {
        String project = """
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
                """.formatted(PROJECT_ID, PROJECT_ID, RESOLVED_BUCKET_NAME, mockUrl + COS_PATH);
        mock.raw().on("GET", PROJECT_PATH).reply(200, project);
        mock.raw().on("GET", CUSTOM_PROJECT_PATH).reply(200, project);

    }

    @Inject
    TextExtractionService textExtraction;

    @Inject
    TextClassificationService classification;

    @Inject
    @ModelName("custom-auth")
    TextExtractionService extractionWithCustomAuthenticator;

    @Inject
    @ModelName("custom-auth")
    TextClassificationService classificationWithCustomAuthenticator;

    @Inject
    @ModelName("custom-project")
    TextExtractionService extractionWithCustomProject;

    @Inject
    @ModelName("custom-project")
    TextClassificationService classificationWithCustomProject;

    @Test
    void should_use_project_default_container_for_extraction() throws Exception {
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

        stubCosDeleteRetries(cosFile());
        assertThat(textExtraction.deleteFile("ignored-bucket", FILE_NAME)).isTrue();

        long started = System.nanoTime();
        assertThat(textExtraction.deleteFile("ignored-bucket", FILE_NAME)).isTrue();

        assertCosDeleteRetriedWithBackoff(cosFile(), started);
    }

    @Test
    void should_use_separate_project_endpoint_for_classification() throws Exception {
        var file = Path.of(ClassLoader.getSystemResource(FILE_NAME).toURI()).toFile();
        mock.raw().on("PUT", cosFile()).reply(200, "");

        assertThat(classification.uploadFile(file)).isTrue();

        assertProjectAndStorageRequests();

        stubCosDeleteRetries(cosFile());
        assertThat(classification.deleteFile("ignored-bucket", FILE_NAME)).isTrue();

        long started = System.nanoTime();
        assertThat(classification.deleteFile("ignored-bucket", FILE_NAME)).isTrue();

        assertCosDeleteRetriedWithBackoff(cosFile(), started);
    }

    @Test
    void should_use_customized_authenticator_for_extraction_project_discovery() {
        String fileName = "extraction.pdf";
        mock.raw().on("PUT", cosFile(fileName)).reply(200, "");

        assertThat(extractionWithCustomAuthenticator.uploadFile(new ByteArrayInputStream(new byte[] { 1 }), fileName)).isTrue();

        assertProjectAndStorageAuthentication(PROJECT_PATH, CUSTOM_TOKEN, fileName);
    }

    @Test
    void should_use_customized_authenticator_for_classification_project_discovery() {
        String fileName = "classification.pdf";
        mock.raw().on("PUT", cosFile(fileName)).reply(200, "");

        assertThat(classificationWithCustomAuthenticator.uploadFile(new ByteArrayInputStream(new byte[] { 1 }), fileName))
                .isTrue();

        assertProjectAndStorageAuthentication(PROJECT_PATH, CUSTOM_TOKEN, fileName);
    }

    @Test
    void should_preserve_customized_project_service_for_extraction() {
        String fileName = "custom-project-extraction.pdf";
        mock.raw().on("PUT", cosFile(fileName)).reply(200, "");

        assertThat(extractionWithCustomProject.uploadFile(new ByteArrayInputStream(new byte[] { 1 }), fileName)).isTrue();

        assertProjectAndStorageAuthentication(CUSTOM_PROJECT_PATH, PROJECT_TOKEN, fileName);
        assertThat(requests("GET", PROJECT_PATH)).isEmpty();
    }

    @Test
    void should_preserve_customized_project_service_for_classification() {
        String fileName = "custom-project-classification.pdf";
        mock.raw().on("PUT", cosFile(fileName)).reply(200, "");

        assertThat(classificationWithCustomProject.uploadFile(new ByteArrayInputStream(new byte[] { 1 }), fileName)).isTrue();

        assertProjectAndStorageAuthentication(CUSTOM_PROJECT_PATH, PROJECT_TOKEN, fileName);
        assertThat(requests("GET", PROJECT_PATH)).isEmpty();
    }

    private void assertProjectAndStorageAuthentication(String projectPath, String projectToken, String fileName) {
        assertThat(requests("GET", projectPath)).hasSize(1);
        assertThat(requests("GET", projectPath).get(0).header("Authorization")).isEqualTo("Bearer " + projectToken);
        assertThat(requests("PUT", cosFile(fileName))).hasSize(1);
        assertThat(requests("PUT", cosFile(fileName)).get(0).header("Authorization")).isEqualTo("Bearer " + CUSTOM_TOKEN);
        assertThat(iamRequests()).isEmpty();
    }

    private static String cosFile(String fileName) {
        return COS_PATH + "/" + RESOLVED_BUCKET_NAME + "/" + fileName;
    }

    @ApplicationScoped
    @ModelName("custom-auth")
    public static class ExtractionAuthenticatorCustomizer implements ModelBuilderCustomizer<TextExtractionService.Builder> {
        @Override
        public void customize(TextExtractionService.Builder builder) {
            builder.authenticator(new FixedAuthenticator(CUSTOM_TOKEN));
        }
    }

    @ApplicationScoped
    @ModelName("custom-auth")
    public static class ClassificationAuthenticatorCustomizer
            implements ModelBuilderCustomizer<TextClassificationService.Builder> {
        @Override
        public void customize(TextClassificationService.Builder builder) {
            builder.authenticator(new FixedAuthenticator(CUSTOM_TOKEN));
        }
    }

    @ApplicationScoped
    @ModelName("custom-project")
    public static class ExtractionProjectServiceCustomizer implements ModelBuilderCustomizer<TextExtractionService.Builder> {
        @Inject
        @ConfigProperty(name = "quarkus.langchain4j.mock-api.url")
        String mockUrl;

        @Override
        public void customize(TextExtractionService.Builder builder) {
            builder.authenticator(new FixedAuthenticator(CUSTOM_TOKEN));
            builder.projectService(ProjectService.builder()
                    .baseUrl(mockUrl + "/custom-projects")
                    .authenticator(new FixedAuthenticator(PROJECT_TOKEN))
                    .build());
        }
    }

    @ApplicationScoped
    @ModelName("custom-project")
    public static class ClassificationProjectServiceCustomizer
            implements ModelBuilderCustomizer<TextClassificationService.Builder> {
        @Inject
        @ConfigProperty(name = "quarkus.langchain4j.mock-api.url")
        String mockUrl;

        @Override
        public void customize(TextClassificationService.Builder builder) {
            builder.authenticator(new FixedAuthenticator(CUSTOM_TOKEN));
            builder.projectService(ProjectService.builder()
                    .baseUrl(mockUrl + "/custom-projects")
                    .authenticator(new FixedAuthenticator(PROJECT_TOKEN))
                    .build());
        }
    }

    public record FixedAuthenticator(String token) implements Authenticator {
        @Override
        public CompletableFuture<String> tokenAsync() {
            return CompletableFuture.completedFuture(token);
        }

        @Override
        public String scheme() {
            return "Bearer";
        }
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
