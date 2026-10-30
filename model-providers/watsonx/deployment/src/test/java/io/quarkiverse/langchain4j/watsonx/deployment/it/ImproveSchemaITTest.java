package io.quarkiverse.langchain4j.watsonx.deployment.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

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
import com.ibm.watsonx.ai.textprocessing.Status;
import com.ibm.watsonx.ai.textprocessing.schema.improve.ImproveSchemaDeleteParameters;
import com.ibm.watsonx.ai.textprocessing.schema.improve.ImproveSchemaParameters;
import com.ibm.watsonx.ai.textprocessing.schema.improve.ImproveSchemaService;

import io.quarkus.test.QuarkusUnitTest;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
public class ImproveSchemaITTest {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");

    static final Schema SCHEMA = Schema.builder()
            .documentType("Invoice")
            .documentDescription("A vendor-issued invoice listing purchased items, prices, and payment information")
            .fields(KvpFields.builder()
                    .add("invoice_date", KvpField.of("The date when the invoice was issued.", "2024-07-10"))
                    .add("invoice_number", KvpField.of("The unique number identifying the invoice.", "INV-2024-001"))
                    .add("total_amount", KvpField.of("The total amount to be paid.", "1250.50"))
                    .build())
            .build();

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.timeout", "5m")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    ImproveSchemaService improveSchemaService;

    @Test
    void should_start_improve_schema_and_complete_successfully() throws Exception {

        var response = improveSchemaService.startImproveSchema(SCHEMA);
        assertNotNull(response.entity());
        assertNotNull(response.entity().parameters());
        assertEquals(SCHEMA, response.entity().parameters().schema());
        assertNotNull(response.entity().results());
        assertNotNull(response.entity().results().status());
        assertNotNull(response.metadata().id());
        assertNotNull(response.metadata().createdAt());
        assertEquals(PROJECT_ID, response.metadata().projectId());

        var status = response.entity().results().status();
        while (!status.equals(Status.FAILED.value()) && !status.equals(Status.COMPLETED.value())) {
            Thread.sleep(2000);
            response = improveSchemaService.fetchRequest(response.metadata().id());
            status = response.entity().results().status();
        }

        assertEquals(Status.COMPLETED.value(), status);
        assertNotNull(response.entity().results().runningAt());
        assertNotNull(response.entity().results().completedAt());
        assertNull(response.entity().results().error());

        var schema = response.entity().results().schema();
        assertNotNull(schema);
        assertNotNull(schema.documentType());
        assertNotNull(schema.documentDescription());
        assertNotNull(schema.fields());
        assertFalse(schema.fields().isEmpty());
    }

    @Test
    void should_improve_schema_and_fetch_successfully() {

        var result = improveSchemaService.improveSchemaAndFetch(SCHEMA);
        assertEquals(Status.COMPLETED.value(), result.status());
        assertNull(result.error());
        assertNotNull(result.runningAt());
        assertNotNull(result.completedAt());

        var schema = result.schema();
        assertNotNull(schema);
        assertNotNull(schema.documentType());
        assertNotNull(schema.documentDescription());
        assertFalse(schema.fields().isEmpty());
        assertTrue(schema.fields().keySet().containsAll(SCHEMA.fields().keySet()));
    }

    @Test
    void should_improve_schema_and_fetch_with_parameters() {

        var parameters = ImproveSchemaParameters.builder()
                .timeout(Duration.ofMinutes(5))
                .build();

        var result = improveSchemaService.improveSchemaAndFetch(SCHEMA, parameters);
        assertEquals(Status.COMPLETED.value(), result.status());
        assertNotNull(result.schema());
        assertFalse(result.schema().fields().isEmpty());
    }

    @Test
    void should_delete_improve_schema_request() {

        var response = improveSchemaService.startImproveSchema(SCHEMA);
        var id = response.metadata().id();

        assertTrue(improveSchemaService.deleteRequest(id, ImproveSchemaDeleteParameters.builder()
                .hardDelete(true)
                .build()));

        var ex = assertThrows(WatsonxException.class, () -> improveSchemaService.fetchRequest(id));
        assertEquals(404, ex.statusCode());
        assertFalse(improveSchemaService.deleteRequest(id));
    }
}
