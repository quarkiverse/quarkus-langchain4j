package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_COS_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Date;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.textprocessing.schema.create.CreateSchemaService;
import com.ibm.watsonx.ai.textprocessing.schema.improve.ImproveSchemaService;
import com.ibm.watsonx.ai.textprocessing.schema.merge.MergeSchemaService;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.QuarkusUnitTest;

public class SchemaServicesInjectTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            // CreateSchema requires COS settings, like TextClassification/TextExtraction.
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.create-schema.cos-url", URL_COS_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.create-schema.document-reference.connection",
                    "document-connection")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.create-schema.document-reference.bucket-name",
                    "document-bucket-name")
            // Merge/Improve have per-service log overrides only.
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.merge-schema.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.improve-schema.log-responses", "true")
            // Named "custom" model configuration.
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.create-schema.cos-url", URL_COS_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.create-schema.document-reference.connection",
                    "custom-document-connection")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.create-schema.document-reference.bucket-name",
                    "custom-document-bucket-name")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.merge-schema.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.custom.improve-schema.log-responses", "true")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType().orElse(null))
                .response(BEARER_TOKEN, new Date())
                .build();
    }

    @Inject
    CreateSchemaService createSchema;

    @Inject
    MergeSchemaService mergeSchema;

    @Inject
    ImproveSchemaService improveSchema;

    @Inject
    @ModelName("custom")
    CreateSchemaService customCreateSchema;

    @Inject
    @ModelName("custom")
    MergeSchemaService customMergeSchema;

    @Inject
    @ModelName("custom")
    ImproveSchemaService customImproveSchema;

    @Test
    void services_are_injectable() {
        assertNotNull(createSchema);
        assertNotNull(mergeSchema);
        assertNotNull(improveSchema);
    }

    @Test
    void custom_services_are_injectable() {
        assertNotNull(customCreateSchema);
        assertNotNull(customMergeSchema);
        assertNotNull(customImproveSchema);
    }

    @Test
    void create_schema_config_is_parsed() {
        var createSchemaConfig = langchain4jWatsonConfig.defaultConfig().createSchema().orElseThrow();
        assertEquals(URL_COS_SERVER, createSchemaConfig.cosUrl());
        assertEquals("document-connection", createSchemaConfig.documentReference().connection());
        assertEquals("document-bucket-name", createSchemaConfig.documentReference().bucketName());
    }

    @Test
    void merge_and_improve_log_overrides_are_parsed() {
        var mergeSchemaConfig = langchain4jWatsonConfig.defaultConfig().mergeSchema().orElseThrow();
        assertEquals(true, mergeSchemaConfig.logRequests().orElse(false));

        var improveSchemaConfig = langchain4jWatsonConfig.defaultConfig().improveSchema().orElseThrow();
        assertEquals(true, improveSchemaConfig.logResponses().orElse(false));
    }

    @Test
    void custom_create_schema_config_is_parsed() {
        var createSchemaConfig = langchain4jWatsonConfig.namedConfig().get("custom").createSchema().orElseThrow();
        assertEquals(URL_COS_SERVER, createSchemaConfig.cosUrl());
        assertEquals("custom-document-connection", createSchemaConfig.documentReference().connection());
        assertEquals("custom-document-bucket-name", createSchemaConfig.documentReference().bucketName());
    }

    @Test
    void custom_merge_and_improve_log_overrides_are_parsed() {
        var mergeSchemaConfig = langchain4jWatsonConfig.namedConfig().get("custom").mergeSchema().orElseThrow();
        assertEquals(true, mergeSchemaConfig.logRequests().orElse(false));

        var improveSchemaConfig = langchain4jWatsonConfig.namedConfig().get("custom").improveSchema().orElseThrow();
        assertEquals(true, improveSchemaConfig.logResponses().orElse(false));
    }
}
