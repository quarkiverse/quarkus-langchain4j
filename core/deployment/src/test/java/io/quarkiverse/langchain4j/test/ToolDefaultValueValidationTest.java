package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.enterprise.inject.spi.DeploymentException;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.quarkus.test.QuarkusUnitTest;

class ToolDefaultValueValidationTest {

    public static class ToolWithInvalidDefault {

        @Tool
        public void search(String query, @P(defaultValue = "not-a-number") int limit) {
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ToolWithInvalidDefault.class))
            .assertException(t -> {
                assertThat(t)
                        .isInstanceOf(DeploymentException.class)
                        .hasMessageContaining("Invalid @P(defaultValue = \"not-a-number\")")
                        .hasMessageContaining("limit");
            });

    @Test
    void test() {
        fail("Should not be called");
    }
}
