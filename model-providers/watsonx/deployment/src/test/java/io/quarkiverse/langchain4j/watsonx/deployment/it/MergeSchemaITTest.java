package io.quarkiverse.langchain4j.watsonx.deployment.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.textprocessing.KvpFields;
import com.ibm.watsonx.ai.textprocessing.KvpFields.KvpField;
import com.ibm.watsonx.ai.textprocessing.Schema;
import com.ibm.watsonx.ai.textprocessing.schema.merge.MergeSchemaDeleteParameters;
import com.ibm.watsonx.ai.textprocessing.schema.merge.MergeSchemaService;

import io.quarkus.test.QuarkusUnitTest;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
public class MergeSchemaITTest {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.timeout", "120s")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    MergeSchemaService mergeSchemaService;

    private static Schema invoiceSchema() {
        var fields = KvpFields.builder()
                .add("invoice_number", KvpField.of("The unique number identifying the invoice.", "INV-2024-001"))
                .add("total_amount", KvpField.of("The total amount to be paid.", "1250.50"))
                .build();

        return Schema.builder()
                .documentType("Invoice")
                .documentDescription("A vendor-issued invoice listing purchased items, prices, and payment information.")
                .fields(fields)
                .build();
    }

    private static Schema receiptSchema() {
        var fields = KvpFields.builder()
                .add("receipt_number", KvpField.of("The unique number identifying the receipt.", "RCPT-2024-001"))
                .add("amount_paid", KvpField.of("The amount that has been paid.", "50.00"))
                .build();

        return Schema.builder()
                .documentType("Receipt")
                .documentDescription("A proof of payment issued after a purchase.")
                .fields(fields)
                .build();
    }

    @Test
    void should_merge_schemas_and_fetch_successfully() throws Exception {

        var result = mergeSchemaService.mergeSchemaAndFetch(List.of(invoiceSchema(), receiptSchema()));

        assertNull(result.error());
        assertEquals("completed", result.status());
        assertNotNull(result.runningAt());
        assertNotNull(result.completedAt());
        assertNotNull(result.schema());
        assertNotNull(result.schema().documentType());
    }

    @Test
    void should_start_merge_and_poll_until_completed() throws Exception {

        var response = mergeSchemaService.startMergeSchema(List.of(invoiceSchema(), receiptSchema()));
        assertNotNull(response.entity());
        assertNotNull(response.entity().parameters());
        assertNotNull(response.entity().results());
        assertNotNull(response.entity().results().status());
        assertNotNull(response.metadata().id());
        assertNotNull(response.metadata().createdAt());
        assertNotNull(response.metadata().projectId());

        var status = response.entity().results().status();
        while (!status.equals("failed") && !status.equals("completed")) {
            Thread.sleep(2000);
            response = mergeSchemaService.fetchRequest(response.metadata().id());
            status = response.entity().results().status();
        }

        assertEquals("completed", status);
        assertNotNull(response.entity().results().completedAt());
        assertNotNull(response.entity().results().runningAt());
        assertNotNull(response.entity().results().schema());
        assertNotNull(response.entity().results().schema().documentType());
    }

    @Test
    void should_delete_merge_request() throws Exception {

        var response = mergeSchemaService.startMergeSchema(List.of(invoiceSchema(), receiptSchema()));
        assertTrue(
                mergeSchemaService.deleteRequest(
                        response.metadata().id(),
                        MergeSchemaDeleteParameters.builder()
                                .hardDelete(true)
                                .build()));

        var ex = assertThrows(WatsonxException.class,
                () -> mergeSchemaService.fetchRequest(response.metadata().id()));
        assertEquals(404, ex.statusCode());
    }
}
