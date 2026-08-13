package io.quarkiverse.langchain4j.watsonx.deployment.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
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
import com.ibm.watsonx.ai.textprocessing.Status;
import com.ibm.watsonx.ai.textprocessing.schema.merge.MergeSchemaDeleteParameters;
import com.ibm.watsonx.ai.textprocessing.schema.merge.MergeSchemaParameters;
import com.ibm.watsonx.ai.textprocessing.schema.merge.MergeSchemaService;

import io.quarkus.test.QuarkusUnitTest;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
public class MergeSchemaITTest {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");

    static final List<Schema> SCHEMAS = List.of(
            Schema.builder()
                    .documentType("Passport")
                    .documentDescription("A passport issued by a government to allow international travel")
                    .fields(KvpFields.builder()
                            .add("passport_number", KvpField.of("The unique number of the passport.", "X1234567"))
                            .add("full_name", KvpField.of("The full name of the holder.", "John Doe"))
                            .build())
                    .build(),
            Schema.builder()
                    .documentType("Driving license")
                    .documentDescription("A driving license issued by a government to allow the holder to drive")
                    .fields(KvpFields.builder()
                            .add("license_number", KvpField.of("The unique number of the license.", "DL-998877"))
                            .add("full_name", KvpField.of("The full name of the holder.", "John Doe"))
                            .build())
                    .build());

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.timeout", "5m")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    MergeSchemaService mergeSchemaService;

    @Test
    void should_start_merge_schema_and_complete_successfully() throws Exception {

        var response = mergeSchemaService.startMergeSchema(SCHEMAS);
        assertNotNull(response.entity());
        assertNotNull(response.entity().parameters());
        assertEquals(SCHEMAS, response.entity().parameters().schemas());
        assertNotNull(response.entity().results());
        assertNotNull(response.entity().results().status());
        assertNotNull(response.metadata().id());
        assertNotNull(response.metadata().createdAt());
        assertEquals(PROJECT_ID, response.metadata().projectId());

        var status = response.entity().results().status();
        while (!status.equals(Status.FAILED.value()) && !status.equals(Status.COMPLETED.value())) {
            Thread.sleep(2000);
            response = mergeSchemaService.fetchRequest(response.metadata().id());
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
    void should_merge_schema_and_fetch_successfully() {

        var result = mergeSchemaService.mergeSchemaAndFetch(SCHEMAS);
        assertEquals(Status.COMPLETED.value(), result.status());
        assertNull(result.error());
        assertNotNull(result.runningAt());
        assertNotNull(result.completedAt());

        var schema = result.schema();
        assertNotNull(schema);
        assertNotNull(schema.documentType());
        assertNotNull(schema.documentDescription());
        assertFalse(schema.fields().isEmpty());
        assertTrue(schema.fields().containsKey("full_name"));
    }

    @Test
    void should_merge_schema_and_fetch_with_parameters() {

        var parameters = MergeSchemaParameters.builder()
                .timeout(Duration.ofMinutes(5))
                .build();

        var result = mergeSchemaService.mergeSchemaAndFetch(SCHEMAS, parameters);
        assertEquals(Status.COMPLETED.value(), result.status());
        assertNotNull(result.schema());
        assertFalse(result.schema().fields().isEmpty());
    }

    @Test
    void should_delete_merge_schema_request() {

        var response = mergeSchemaService.startMergeSchema(SCHEMAS);
        var id = response.metadata().id();

        assertTrue(mergeSchemaService.deleteRequest(id, MergeSchemaDeleteParameters.builder()
                .hardDelete(true)
                .build()));

        var ex = assertThrows(WatsonxException.class, () -> mergeSchemaService.fetchRequest(id));
        assertEquals(404, ex.statusCode());
        assertFalse(mergeSchemaService.deleteRequest(id));
    }
}
