package io.quarkiverse.langchain4j.watsonx.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.textprocessing.schema.cluster.ClusterSchemaService;

import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.config.ConfigValidationException;

public class ClusterSchemaMissingConfigTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .assertException(t -> {
                assertThat(t.getClass().getName()).isEqualTo(ConfigValidationException.class.getName());
                assertThat(t)
                        .hasMessageContaining("quarkus.langchain4j.watsonx.base-url")
                        .hasMessageContaining("quarkus.langchain4j.watsonx.api-key")
                        .hasMessageContaining("quarkus.langchain4j.watsonx.project-id")
                        .hasMessageContaining("quarkus.langchain4j.watsonx.space-id");
            });

    @Inject
    ClusterSchemaService clusterSchemaService;

    @Test
    void test() {
        fail("Should not be called");
    }
}
