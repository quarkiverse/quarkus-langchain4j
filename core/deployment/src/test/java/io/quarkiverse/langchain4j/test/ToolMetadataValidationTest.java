package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ValidationException;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import io.quarkus.test.QuarkusUnitTest;

public class ToolMetadataValidationTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(InvalidMetadataTool.class))
            .assertException(arg0 -> {
                assertThat(arg0)
                        .isInstanceOf(ValidationException.class)
                        .hasMessageContaining("Invalid metadata JSON for tool invalidMetadataTool");
            });

    public static class InvalidMetadataTool {
        @Tool(name = "invalidMetadataTool", value = "Tool with invalid metadata", metadata = "{invalid}")
        public void toolCall() {

        }
    }

    @Test
    void test() {
        // Should not be called
    }
}
