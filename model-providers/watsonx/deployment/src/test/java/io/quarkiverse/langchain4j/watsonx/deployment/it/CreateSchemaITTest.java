package io.quarkiverse.langchain4j.watsonx.deployment.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.ibm.watsonx.ai.textprocessing.Status;
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

    static final String FILE_NAME = "invoice.pdf";

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
            // The timeout is also used as the deadline of the createSchemaAndFetch methods.
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.timeout", "5m")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.schema.create.cos-url", CLOUD_OBJECT_STORAGE_URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.schema.create.document-reference.connection",
                    DOCUMENT_REFERENCE_CONNECTION_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.schema.create.document-reference.bucket-name",
                    DOCUMENT_REFERENCE_BUCKET)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addAsResource(FILE_NAME));

    @Inject
    CreateSchemaService createSchemaService;

    @Test
    void should_upload_file_and_complete_create_schema_successfully() throws Exception {

        var file = Path.of(ClassLoader.getSystemResource(FILE_NAME).toURI()).toFile();

        var parameters = CreateSchemaParameters.builder()
                .languages(Language.ENGLISH)
                .build();

        var response = createSchemaService.uploadAndStartCreateSchema(file, parameters);
        assertNotNull(response.entity());
        assertNotNull(response.entity().documentReference().connection());
        assertEquals(DOCUMENT_REFERENCE_CONNECTION_ID, response.entity().documentReference().connection().id());
        assertEquals(FILE_NAME, response.entity().documentReference().location().fileName());
        assertEquals(DOCUMENT_REFERENCE_BUCKET, response.entity().documentReference().location().bucket());
        assertNotNull(response.entity().parameters());
        assertNotNull(response.entity().parameters().languages());
        assertTrue(response.entity().parameters().languages().size() == 1);
        assertNotNull(response.entity().results());
        assertNotNull(response.entity().results().status());
        assertNotNull(response.metadata().id());
        assertNotNull(response.metadata().createdAt());
        assertEquals(PROJECT_ID, response.metadata().projectId());

        var status = response.entity().results().status();
        while (!status.equals(Status.FAILED.value()) && !status.equals(Status.COMPLETED.value())) {
            Thread.sleep(2000);
            response = createSchemaService.fetchRequest(response.metadata().id());
            status = response.entity().results().status();
        }

        assertEquals(Status.COMPLETED.value(), status);
        assertNotNull(response.entity().results().runningAt());
        assertNotNull(response.entity().results().completedAt());
        assertNull(response.entity().results().error());
        assertNotNull(response.metadata().modifiedAt());

        var schema = response.entity().results().schema();
        assertNotNull(schema);
        assertNotNull(schema.documentType());
        assertNotNull(schema.documentDescription());
        assertNotNull(schema.fields());
        assertFalse(schema.fields().isEmpty());

        assertTrue(createSchemaService.deleteFile(DOCUMENT_REFERENCE_BUCKET, FILE_NAME));
    }

    @Test
    void should_upload_create_schema_and_fetch_successfully() throws Exception {

        var file = Path.of(ClassLoader.getSystemResource(FILE_NAME).toURI()).toFile();

        var parameters = CreateSchemaParameters.builder()
                .languages(Language.ENGLISH)
                .build();

        var result = createSchemaService.uploadCreateSchemaAndFetch(file, parameters);
        assertEquals(Status.COMPLETED.value(), result.status());
        assertNull(result.error());
        assertNotNull(result.runningAt());
        assertNotNull(result.completedAt());
        assertTrue(result.numberPagesProcessed() > 0);
        assertNotNull(result.schema());
        assertNotNull(result.schema().documentType());
        assertNotNull(result.schema().documentDescription());
        assertFalse(result.schema().fields().isEmpty());

        assertTrue(createSchemaService.deleteFile(DOCUMENT_REFERENCE_BUCKET, FILE_NAME));

        parameters = CreateSchemaParameters.builder()
                .languages(Language.ENGLISH)
                .removeUploadedFile(true)
                .build();

        result = createSchemaService.uploadCreateSchemaAndFetch(file, parameters);
        assertEquals(Status.COMPLETED.value(), result.status());
        assertNotNull(result.schema());

        // Wait for async deletion
        Thread.sleep(500);
    }

    @Test
    void should_create_schema_and_fetch_with_a_file_already_uploaded() throws Exception {

        var file = Path.of(ClassLoader.getSystemResource(FILE_NAME).toURI()).toFile();
        assertTrue(createSchemaService.uploadFile(file));

        var parameters = CreateSchemaParameters.builder()
                .languages(Language.ENGLISH)
                .maxPagesToProcess(1)
                .build();

        var result = createSchemaService.createSchemaAndFetch(FILE_NAME, parameters);
        assertEquals(Status.COMPLETED.value(), result.status());
        assertNull(result.error());
        assertNotNull(result.schema());
        assertFalse(result.schema().fields().isEmpty());

        assertTrue(createSchemaService.deleteFile(DOCUMENT_REFERENCE_BUCKET, FILE_NAME));
    }

    @Test
    void should_upload_create_schema_and_fetch_from_inputstream_successfully() throws Exception {

        var inputStream = ClassLoader.getSystemResourceAsStream(FILE_NAME);

        var result = createSchemaService.uploadCreateSchemaAndFetch(inputStream, FILE_NAME);
        assertEquals(Status.COMPLETED.value(), result.status());
        assertNotNull(result.schema());
        assertFalse(result.schema().fields().isEmpty());

        assertTrue(createSchemaService.deleteFile(DOCUMENT_REFERENCE_BUCKET, FILE_NAME));

        var parameters = CreateSchemaParameters.builder()
                .removeUploadedFile(true)
                .build();

        inputStream = ClassLoader.getSystemResourceAsStream(FILE_NAME);
        result = createSchemaService.uploadCreateSchemaAndFetch(inputStream, FILE_NAME, parameters);
        assertEquals(Status.COMPLETED.value(), result.status());
        assertNotNull(result.schema());

        // Wait for async deletion
        Thread.sleep(500);
    }

    @Test
    void should_return_grounding_hints_when_grounding_is_enabled() throws Exception {

        var file = Path.of(ClassLoader.getSystemResource(FILE_NAME).toURI()).toFile();

        var parameters = CreateSchemaParameters.builder()
                .languages(Language.ENGLISH)
                .mode(Mode.HIGH_QUALITY)
                .enableGrounding(true)
                .maxPagesToProcess(1)
                .removeUploadedFile(true)
                .build();

        var result = createSchemaService.uploadCreateSchemaAndFetch(file, parameters);
        assertEquals(Status.COMPLETED.value(), result.status());

        var groundingHints = result.groundingHints();
        assertNotNull(groundingHints);
        assertFalse(groundingHints.fieldNames().isEmpty());

        for (var fieldName : groundingHints.fieldNames()) {
            assertTrue(groundingHints.hasField(fieldName));
            assertNotNull(groundingHints.field(fieldName));
            assertEquals(4, groundingHints.bbox(fieldName).size());
            assertTrue(groundingHints.pageNumber(fieldName) >= 1);
        }

        // Wait for async deletion
        Thread.sleep(500);
    }

    @Test
    void should_delete_create_schema_request() throws Exception {

        var file = Path.of(ClassLoader.getSystemResource(FILE_NAME).toURI()).toFile();

        var response = createSchemaService.uploadAndStartCreateSchema(file);
        var id = response.metadata().id();

        assertTrue(createSchemaService.deleteRequest(id, CreateSchemaDeleteParameters.builder()
                .hardDelete(true)
                .build()));

        var ex = assertThrows(WatsonxException.class, () -> createSchemaService.fetchRequest(id));
        assertEquals(404, ex.statusCode());

        // The request no longer exists, so the deletion cannot be performed.
        assertFalse(createSchemaService.deleteRequest(id));

        assertTrue(createSchemaService.deleteFile(DOCUMENT_REFERENCE_BUCKET, FILE_NAME));
    }
}
