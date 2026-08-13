package io.quarkiverse.langchain4j.watsonx.deployment.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.ibm.watsonx.ai.textprocessing.Status;
import com.ibm.watsonx.ai.textprocessing.schema.cluster.ClusterSchemaDeleteParameters;
import com.ibm.watsonx.ai.textprocessing.schema.cluster.ClusterSchemaParameters;
import com.ibm.watsonx.ai.textprocessing.schema.cluster.ClusterSchemaService;
import com.ibm.watsonx.ai.textprocessing.schema.cluster.ClusterSchemas;

import io.quarkus.test.QuarkusUnitTest;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
public class ClusterSchemaITTest {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");

    static final List<ClusterSchemas> SCHEMAS = List.of(
            new ClusterSchemas("invoice-1.pdf", Schema.builder()
                    .documentType("Invoice")
                    .documentDescription("A vendor-issued invoice listing purchased items, prices and payment information")
                    .fields(KvpFields.builder()
                            .add("invoice_number", KvpField.of("The unique number identifying the invoice.", "INV-2024-001"))
                            .add("total_amount", KvpField.of("The total amount to be paid.", "1250.50"))
                            .build())
                    .build()),
            new ClusterSchemas("invoice-2.pdf", Schema.builder()
                    .documentType("Bill")
                    .documentDescription("A bill sent to a customer with the list of the purchased goods and the amount due")
                    .fields(KvpFields.builder()
                            .add("bill_number", KvpField.of("The unique number identifying the bill.", "B-77321"))
                            .add("amount_due", KvpField.of("The amount the customer has to pay.", "980.00"))
                            .build())
                    .build()),
            new ClusterSchemas("passport-1.pdf", Schema.builder()
                    .documentType("Passport")
                    .documentDescription("A passport issued by a government to allow international travel")
                    .fields(KvpFields.builder()
                            .add("passport_number", KvpField.of("The unique number of the passport.", "X1234567"))
                            .add("full_name", KvpField.of("The full name of the holder.", "John Doe"))
                            .build())
                    .build()),
            new ClusterSchemas("passport-2.pdf", Schema.builder()
                    .documentType("Travel document")
                    .documentDescription("An identity document that allows the holder to cross international borders")
                    .fields(KvpFields.builder()
                            .add("document_number", KvpField.of("The unique number of the document.", "Y7654321"))
                            .add("full_name", KvpField.of("The full name of the holder.", "Jane Doe"))
                            .build())
                    .build()));

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.timeout", "5m")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    ClusterSchemaService clusterSchemaService;

    @Test
    void should_start_cluster_schema_and_complete_successfully() throws Exception {

        var response = clusterSchemaService.startClusterSchema(SCHEMAS);
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
            response = clusterSchemaService.fetchRequest(response.metadata().id());
            status = response.entity().results().status();
        }

        assertEquals(Status.COMPLETED.value(), status);
        assertNotNull(response.entity().results().runningAt());
        assertNotNull(response.entity().results().completedAt());
        assertNull(response.entity().results().error());

        assertClusters(response.entity().results().schemas(), SCHEMAS);
    }

    @Test
    void should_cluster_schema_and_fetch_successfully() {

        var clusters = clusterSchemaService.clusterSchemaAndFetch(SCHEMAS);
        assertClusters(clusters, SCHEMAS);
    }

    @Test
    void should_cluster_schema_and_fetch_with_varargs() {

        var invoice = SCHEMAS.get(0);
        var passport = SCHEMAS.get(2);

        var clusters = clusterSchemaService.clusterSchemaAndFetch(invoice, passport);
        assertClusters(clusters, List.of(invoice, passport));
    }

    @Test
    void should_cluster_schema_and_fetch_with_parameters() {

        var parameters = ClusterSchemaParameters.builder()
                .schemas(SCHEMAS)
                .build();

        var clusters = clusterSchemaService.clusterSchemaAndFetch(parameters, List.<ClusterSchemas> of());
        assertClusters(clusters, SCHEMAS);
    }

    @Test
    void should_delete_cluster_schema_request() {

        var response = clusterSchemaService.startClusterSchema(SCHEMAS);
        var id = response.metadata().id();

        assertTrue(clusterSchemaService.deleteRequest(id, ClusterSchemaDeleteParameters.builder()
                .hardDelete(true)
                .build()));

        var ex = assertThrows(WatsonxException.class, () -> clusterSchemaService.fetchRequest(id));
        assertEquals(404, ex.statusCode());
        assertFalse(clusterSchemaService.deleteRequest(id));
    }

    private void assertClusters(List<List<ClusterSchemas>> clusters, List<ClusterSchemas> expected) {
        assertNotNull(clusters);
        assertFalse(clusters.isEmpty());
        assertTrue(clusters.size() <= expected.size());

        var clustered = clusters.stream().flatMap(List::stream).toList();
        assertEquals(expected.size(), clustered.size());

        var documentNames = clustered.stream().map(ClusterSchemas::documentName).toList();
        for (ClusterSchemas schema : expected) {
            assertEquals(1, documentNames.stream().filter(name -> name.equals(schema.documentName())).count());
        }

        for (List<ClusterSchemas> cluster : clusters) {
            assertFalse(cluster.isEmpty());
            cluster.forEach(schema -> assertNotNull(schema.schema()));
        }
    }
}
