package io.quarkiverse.langchain4j.watsonx.deployment.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.textprocessing.Language;
import com.ibm.watsonx.ai.textprocessing.Mode;
import com.ibm.watsonx.ai.textprocessing.schema.create.CreateSchemaDeleteParameters;
import com.ibm.watsonx.ai.textprocessing.schema.create.CreateSchemaParameters;
import com.ibm.watsonx.ai.textprocessing.schema.create.CreateSchemaService;

import io.quarkus.test.QuarkusUnitTest;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_DOCUMENT_REFERENCE_CONNECTION_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_DOCUMENT_REFERENCE_BUCKET", matches = ".+")
@EnabledIfEnvironmentVariable(named = "CLOUD_OBJECT_STORAGE_URL", matches = ".+")
public class CreateSchemaITTest {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");
    static final String DOCUMENT_REFERENCE_CONNECTION_ID = System.getenv("WATSONX_DOCUMENT_REFERENCE_CONNECTION_ID");
    static final String DOCUMENT_REFERENCE_BUCKET = System.getenv("WATSONX_DOCUMENT_REFERENCE_BUCKET");
    static final String CLOUD_OBJECT_STORAGE_URL = System.getenv("CLOUD_OBJECT_STORAGE_URL");

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.timeout", "120s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.create-schema.cos-url", CLOUD_OBJECT_STORAGE_URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.create-schema.document-reference.connection",
                    DOCUMENT_REFERENCE_CONNECTION_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.create-schema.document-reference.bucket-name",
                    DOCUMENT_REFERENCE_BUCKET)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addAsResource("invoice.pdf"));

    @Inject
    CreateSchemaService createSchemaService;

    @Test
    void should_upload_file_and_complete_the_create_schema_successfully() throws Exception {

        var file = Path.of(ClassLoader.getSystemResource("invoice.pdf").toURI()).toFile();

        var parameters = CreateSchemaParameters.builder()
                .mode(Mode.HIGH_QUALITY)
                .languages(Language.ENGLISH)
                .build();

        var response = createSchemaService.uploadCreateSchemaAndFetch(file, parameters);
        assertThat(response.schema().documentType()).isEqualTo("Invoice");
    }

    @Test
    void should_start_create_schema_and_poll_until_completed() throws Exception {

        var file = Path.of(ClassLoader.getSystemResource("invoice.pdf").toURI()).toFile();

        var parameters = CreateSchemaParameters.builder()
                .mode(Mode.HIGH_QUALITY)
                .languages(Language.ENGLISH)
                .build();

        var response = createSchemaService.uploadAndStartCreateSchema(file, parameters);
        assertNotNull(response.entity());
        assertNotNull(response.entity().documentReference().connection());
        assertNotNull(response.entity().documentReference().connection().id());
        assertNotNull(response.entity().parameters());
        assertNotNull(response.entity().results());
        assertNotNull(response.entity().results().status());
        assertNotNull(response.metadata().id());
        assertNotNull(response.metadata().createdAt());
        assertNotNull(response.metadata().projectId());

        var status = response.entity().results().status();
        while (!status.equals("failed") && !status.equals("completed")) {
            Thread.sleep(2000);
            response = createSchemaService.fetchRequest(response.metadata().id());
            status = response.entity().results().status();
        }

        assertEquals("completed", status);
        assertNotNull(response.entity().results().completedAt());
        assertNotNull(response.entity().results().runningAt());
        assertNotNull(response.entity().results().schema());
        assertEquals("Invoice", response.entity().results().schema().documentType());

        assertTrue(createSchemaService.deleteFile(DOCUMENT_REFERENCE_BUCKET, "invoice.pdf"));
    }

    @Test
    void should_delete_create_schema_request() throws Exception {

        var file = Path.of(ClassLoader.getSystemResource("invoice.pdf").toURI()).toFile();

        var response = createSchemaService.uploadAndStartCreateSchema(file);
        assertTrue(
                createSchemaService.deleteRequest(
                        response.metadata().id(),
                        CreateSchemaDeleteParameters.builder()
                                .hardDelete(true)
                                .build()));

        var ex = assertThrows(WatsonxException.class,
                () -> createSchemaService.fetchRequest(response.metadata().id()));
        assertEquals(404, ex.statusCode());

        assertTrue(createSchemaService.deleteFile(DOCUMENT_REFERENCE_BUCKET, "invoice.pdf"));
    }

    @Test
    void should_upload_create_schema_and_fetch_from_inputstream_successfully() throws Exception {

        var filename = "invoice.pdf";
        var inputstream = ClassLoader.getSystemResourceAsStream(filename);

        var parameters = CreateSchemaParameters.builder()
                .mode(Mode.HIGH_QUALITY)
                .languages(Language.ENGLISH)
                .build();

        var result = createSchemaService.uploadCreateSchemaAndFetch(inputstream, filename, parameters);
        assertNull(result.error());
        assertEquals("completed", result.status());
        assertNotNull(result.schema());
        assertEquals("Invoice", result.schema().documentType());

        assertTrue(createSchemaService.deleteFile(DOCUMENT_REFERENCE_BUCKET, filename));
    }
}
