package io.quarkiverse.langchain4j.watsonx.deployment.it;

import static java.util.Objects.isNull;
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
import com.ibm.watsonx.ai.textprocessing.KvpFields;
import com.ibm.watsonx.ai.textprocessing.KvpFields.KvpField;
import com.ibm.watsonx.ai.textprocessing.Language;
import com.ibm.watsonx.ai.textprocessing.Schema;
import com.ibm.watsonx.ai.textprocessing.SemanticConfig.SchemaMergeStrategy;
import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationDeleteParameters;
import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationParameters;
import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationSemanticConfig;
import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationService;

import io.quarkus.test.QuarkusUnitTest;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_DOCUMENT_REFERENCE_CONNECTION_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_DOCUMENT_REFERENCE_BUCKET", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_RESULTS_REFERENCE_CONNECTION_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_DOCUMENT_REFERENCE_BUCKET", matches = ".+")
@EnabledIfEnvironmentVariable(named = "CLOUD_OBJECT_STORAGE_URL", matches = ".+")
public class TextClassificationITTest {

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
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.timeout", "30s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.cos-url", CLOUD_OBJECT_STORAGE_URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.document-reference.connection",
                    DOCUMENT_REFERENCE_CONNECTION_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.document-reference.bucket-name",
                    DOCUMENT_REFERENCE_BUCKET)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addAsResource("invoice.pdf"));

    @Inject
    TextClassificationService classificationService;

    @Test
    void should_upload_file_and_complete_classification_successfully() throws Exception {

        var file = Path.of(ClassLoader.getSystemResource("invoice.pdf").toURI()).toFile();

        var parameters = TextClassificationParameters.builder()
                .languages(Language.ENGLISH)
                .build();

        var response = classificationService.uploadAndStartClassification(file, parameters);
        assertNotNull(response.entity());
        assertNull(response.entity().custom());
        assertNotNull(response.entity().documentReference().connection());
        assertNotNull(response.entity().documentReference().connection().id());
        assertNotNull(response.entity().parameters());
        assertNotNull(response.entity().parameters().languages());
        assertTrue(response.entity().parameters().languages().size() == 1);
        assertNotNull(response.entity().results());
        assertNotNull(response.entity().results().status());
        assertNotNull(response.entity().results().numberPagesProcessed());
        assertNotNull(response.metadata().id());
        assertNotNull(response.metadata().createdAt());
        assertNotNull(response.metadata().projectId());

        var status = response.entity().results().status();
        while (!status.equals("failed") && !status.equals("completed")) {
            Thread.sleep(2000);
            response = classificationService.fetchClassificationRequest(response.metadata().id());
            status = response.entity().results().status();
        }

        assertEquals("completed", status);
        assertNotNull(response.entity());
        assertNull(response.entity().custom());
        assertNotNull(response.entity().documentReference().connection());
        assertNotNull(response.entity().documentReference().connection().id());
        assertNotNull(response.entity().parameters());
        assertNotNull(response.entity().parameters().languages());
        assertTrue(response.entity().parameters().languages().size() == 1);
        assertNotNull(response.entity().results());
        assertNotNull(response.entity().results().completedAt());
        assertNotNull(response.entity().results().runningAt());
        assertNotNull(response.entity().results().status());
        assertNotNull(response.entity().results().numberPagesProcessed());
        assertNotNull(response.metadata().id());
        assertNotNull(response.metadata().createdAt());
        assertNotNull(response.metadata().modifiedAt());
        assertNotNull(response.metadata().projectId());
        assertTrue(response.entity().results().documentClassified());
        assertEquals("Invoice", response.entity().results().documentType());

        assertTrue(classificationService.deleteFile(DOCUMENT_REFERENCE_BUCKET, "invoice.pdf"));
    }

    @Test
    void should_upload_classify_and_fetch_file_successfully() throws Exception {

        var file = Path.of(ClassLoader.getSystemResource("invoice.pdf").toURI()).toFile();

        var parameters = TextClassificationParameters.builder()
                .languages(Language.ENGLISH)
                .build();

        var result = classificationService.uploadClassifyAndFetch(file, parameters);
        assertNull(result.error());
        assertNotNull(result.completedAt());
        assertNotNull(result.numberPagesProcessed());
        assertNotNull(result.runningAt());
        assertTrue(result.documentClassified());
        assertEquals("Invoice", result.documentType());
        assertEquals("completed", result.status());

        parameters = TextClassificationParameters.builder()
                .removeUploadedFile(true)
                .build();

        result = classificationService.uploadClassifyAndFetch(file, parameters);
        assertEquals("Invoice", result.documentType());

        // Wait for async deletion
        Thread.sleep(500);
    }

    @Test
    void should_delete_classification_request() throws Exception {

        var file = Path.of(ClassLoader.getSystemResource("invoice.pdf").toURI()).toFile();

        var parameters = TextClassificationParameters.builder()
                .languages(Language.ENGLISH)
                .build();

        var response = classificationService.uploadAndStartClassification(file, parameters);
        assertTrue(
                classificationService.deleteRequest(
                        response.metadata().id(),
                        TextClassificationDeleteParameters.builder()
                                .hardDelete(true)
                                .build()));

        var ex = assertThrows(WatsonxException.class,
                () -> classificationService.fetchClassificationRequest(response.metadata().id()));
        assertEquals(404, ex.statusCode());
    }

    @Test
    void should_upload_classify_and_fetch_file_from_inputstream_successfully() throws Exception {

        var filename = "invoice.pdf";
        var inputstream = ClassLoader.getSystemResourceAsStream(filename);

        var parameters = TextClassificationParameters.builder()
                .languages(Language.ENGLISH)
                .build();

        var result = classificationService.uploadClassifyAndFetch(inputstream, filename);
        assertEquals("Invoice", result.documentType());
        assertTrue(classificationService.deleteFile(DOCUMENT_REFERENCE_BUCKET, filename));

        parameters = TextClassificationParameters.builder()
                .removeUploadedFile(true)
                .build();

        inputstream = ClassLoader.getSystemResourceAsStream(filename);
        result = classificationService.uploadClassifyAndFetch(inputstream, filename, parameters);
        assertEquals("Invoice", result.documentType());

        // Wait for async deletion
        Thread.sleep(500);
    }

    @Test
    void should_classify_documents_according_to_semantic_config() throws Exception {

        var invoice = Path.of(ClassLoader.getSystemResource("invoice.pdf").toURI()).toFile();
        var unclassified = Path.of(ClassLoader.getSystemResource("test.pdf").toURI()).toFile();

        var fields = KvpFields.builder()
                .add("invoice_date", KvpField.of("The date when the invoice was issued.", "2024-07-10"))
                .add("invoice_number", KvpField.of("The unique number identifying the invoice.", "INV-2024-001"))
                .add("total_amount", KvpField.of("The total amount to be paid.", "1250.50"))
                .build();

        var semanticConfig = TextClassificationSemanticConfig.builder()
                .schemasMergeStrategy(SchemaMergeStrategy.REPLACE)
                .schemas(
                        Schema.builder()
                                .documentDescription(
                                        "A vendor-issued invoice listing purchased items, prices, and payment information")
                                .documentType("My-Invoice")
                                .fields(fields)
                                .additionalPromptInstructions("The document contains a table with all the data")
                                .build())
                .build();

        var parameters = TextClassificationParameters.builder()
                .languages(Language.ENGLISH)
                .semanticConfig(semanticConfig)
                .build();

        var result = classificationService.uploadClassifyAndFetch(invoice, parameters);
        assertTrue(result.documentClassified());
        assertEquals("My-Invoice", result.documentType());

        result = classificationService.uploadClassifyAndFetch(unclassified, parameters);
        assertFalse(result.documentClassified());
        assertTrue(isNull(result.documentType()) || result.documentType().isBlank());
    }
}
