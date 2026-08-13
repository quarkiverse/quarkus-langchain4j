package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_COS_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.textprocessing.schema.cluster.ClusterSchemaService;
import com.ibm.watsonx.ai.textprocessing.schema.create.CreateSchemaService;
import com.ibm.watsonx.ai.textprocessing.schema.improve.ImproveSchemaService;
import com.ibm.watsonx.ai.textprocessing.schema.merge.MergeSchemaService;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.QuarkusUnitTest;

public class SchemaInjectTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.schema.create.cos-url", URL_COS_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.schema.create.document-reference.connection",
                    "document-connection")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.schema.create.document-reference.bucket-name",
                    "document-bucket-name")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.schema.create.log-requests", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.schema.improve.log-requests", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.schema.merge.log-requests", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.schema.cluster.log-requests", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.schema.create.cos-url", URL_COS_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.schema.create.document-reference.connection",
                    "custom-document-connection")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.schema.create.document-reference.bucket-name",
                    "custom-document-bucket-name")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.schema.create.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.schema.improve.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.schema.merge.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.schema.cluster.log-requests", "true")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType().orElse(null))
                .response(BEARER_TOKEN, new Date())
                .build();
    }

    @Inject
    CreateSchemaService createSchemaService;

    @Inject
    ImproveSchemaService improveSchemaService;

    @Inject
    MergeSchemaService mergeSchemaService;

    @Inject
    ClusterSchemaService clusterSchemaService;

    @Inject
    @ModelName("custom")
    CreateSchemaService customCreateSchemaService;

    @Inject
    @ModelName("custom")
    ImproveSchemaService customImproveSchemaService;

    @Inject
    @ModelName("custom")
    MergeSchemaService customMergeSchemaService;

    @Inject
    @ModelName("custom")
    ClusterSchemaService customClusterSchemaService;

    @Test
    void schemaBeansTest() {
        assertNotNull(createSchemaService);
        assertNotNull(improveSchemaService);
        assertNotNull(mergeSchemaService);
        assertNotNull(clusterSchemaService);
        assertNotNull(customCreateSchemaService);
        assertNotNull(customImproveSchemaService);
        assertNotNull(customMergeSchemaService);
        assertNotNull(customClusterSchemaService);
    }

    @Test
    void schemaPropertiesTest() {
        var schemaConfig = langchain4jWatsonConfig.defaultConfig().schema();
        var createSchemaConfig = schemaConfig.create().orElseThrow();
        assertEquals(URL_COS_SERVER, createSchemaConfig.cosUrl());
        assertEquals("document-connection", createSchemaConfig.documentReference().connection());
        assertEquals("document-bucket-name", createSchemaConfig.documentReference().bucketName());
        assertEquals(false, createSchemaConfig.logRequests().orElse(false));
        assertEquals(false, schemaConfig.improve().logRequests().orElse(false));
        assertEquals(false, schemaConfig.merge().logRequests().orElse(false));
        assertEquals(false, schemaConfig.cluster().logRequests().orElse(false));
    }

    @Test
    void customSchemaPropertiesTest() {
        var schemaConfig = langchain4jWatsonConfig.namedConfig().get("custom").schema();
        var createSchemaConfig = schemaConfig.create().orElseThrow();
        assertEquals("custom-document-connection", createSchemaConfig.documentReference().connection());
        assertEquals("custom-document-bucket-name", createSchemaConfig.documentReference().bucketName());
        assertTrue(createSchemaConfig.logRequests().orElse(false));
        assertTrue(schemaConfig.improve().logRequests().orElse(false));
        assertTrue(schemaConfig.merge().logRequests().orElse(false));
        assertTrue(schemaConfig.cluster().logRequests().orElse(false));
    }
}
