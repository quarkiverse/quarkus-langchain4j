package io.quarkiverse.langchain4j.watsonx.deployment.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.ibm.watsonx.ai.textprocessing.schema.improve.ImproveSchemaDeleteParameters;
import com.ibm.watsonx.ai.textprocessing.schema.improve.ImproveSchemaService;

import io.quarkus.test.QuarkusUnitTest;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
public class ImproveSchemaITTest {

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
    ImproveSchemaService improveSchemaService;

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

    @Test
    void should_improve_schema_and_fetch_successfully() throws Exception {

        var result = improveSchemaService.improveSchemaAndFetch(invoiceSchema());

        assertNull(result.error());
        assertEquals("completed", result.status());
        assertNotNull(result.runningAt());
        assertNotNull(result.completedAt());
        assertNotNull(result.schema());
        assertNotNull(result.schema().documentType());
    }

    @Test
    void should_start_improve_and_poll_until_completed() throws Exception {

        var response = improveSchemaService.startImproveSchema(invoiceSchema());
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
            response = improveSchemaService.fetchRequest(response.metadata().id());
            status = response.entity().results().status();
        }

        assertEquals("completed", status);
        assertNotNull(response.entity().results().completedAt());
        assertNotNull(response.entity().results().runningAt());
        assertNotNull(response.entity().results().schema());
        assertNotNull(response.entity().results().schema().documentType());
    }

    @Test
    void should_delete_improve_request() throws Exception {

        var response = improveSchemaService.startImproveSchema(invoiceSchema());
        assertTrue(
                improveSchemaService.deleteRequest(
                        response.metadata().id(),
                        ImproveSchemaDeleteParameters.builder()
                                .hardDelete(true)
                                .build()));

        var ex = assertThrows(WatsonxException.class,
                () -> improveSchemaService.fetchRequest(response.metadata().id()));
        assertEquals(404, ex.statusCode());
    }
}
