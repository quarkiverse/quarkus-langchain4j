package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrails;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrails;
import io.quarkiverse.langchain4j.runtime.ToolsRecorder;
import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;
import io.quarkus.test.QuarkusUnitTest;

class ToolGuardrailsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            TestTools.class,
                            ValidationInputGuardrail.class,
                            TransformInputGuardrail.class,
                            FilterOutputGuardrail.class));

    @Test
    void testInputGuardrails_metadata() {
        // Verify that input guardrails are properly registered in metadata
        List<ToolMethodCreateInfo> methodCreateInfos = ToolsRecorder.getMetadata().get(TestTools.class.getName());
        assertThat(methodCreateInfos).isNotNull();

        // Find validatedTool and verify it has input guardrails
        ToolMethodCreateInfo validatedToolInfo = methodCreateInfos.stream()
                .filter(info -> "validatedTool".equals(info.toolSpecification().name()))
                .findFirst()
                .orElse(null);
        assertThat(validatedToolInfo).isNotNull();
        assertThat(validatedToolInfo.getInputGuardrails()).isNotNull();
        assertThat(validatedToolInfo.getInputGuardrails().hasGuardrails()).isTrue();
        assertThat(validatedToolInfo.getInputGuardrails().getClassNames())
                .contains("io.quarkiverse.langchain4j.test.guardrails.ToolGuardrailsTest$ValidationInputGuardrail");
    }

    @Test
    void testOutputGuardrails_metadata() {
        // Verify that output guardrails are properly registered in metadata
        List<ToolMethodCreateInfo> methodCreateInfos = ToolsRecorder.getMetadata().get(TestTools.class.getName());
        assertThat(methodCreateInfos).isNotNull();

        // Find filteredTool and verify it has output guardrails
        ToolMethodCreateInfo filteredToolInfo = methodCreateInfos.stream()
                .filter(info -> "filteredTool".equals(info.toolSpecification().name()))
                .findFirst()
                .orElse(null);
        assertThat(filteredToolInfo).isNotNull();
        assertThat(filteredToolInfo.getOutputGuardrails()).isNotNull();
        assertThat(filteredToolInfo.getOutputGuardrails().hasGuardrails()).isTrue();
        assertThat(filteredToolInfo.getOutputGuardrails().getClassNames())
                .contains("io.quarkiverse.langchain4j.test.guardrails.ToolGuardrailsTest$FilterOutputGuardrail");
    }

    @Test
    void testMultipleGuardrails_metadata() {
        // Verify that multiple guardrails can be registered
        List<ToolMethodCreateInfo> methodCreateInfos = ToolsRecorder.getMetadata().get(TestTools.class.getName());
        assertThat(methodCreateInfos).isNotNull();

        // Verify we have all expected tools
        assertThat(methodCreateInfos).hasSize(3);
        assertThat(methodCreateInfos).extracting(info -> info.toolSpecification().name())
                .containsExactlyInAnyOrder("validatedTool", "transformedTool", "filteredTool");
    }

    // Test Tools
    private static class TestTools {

        @Tool
        @ToolInputGuardrails({ ValidationInputGuardrail.class })
        String validatedTool(String input) {
            return "Processed: " + input;
        }

        @Tool
        @ToolInputGuardrails({ TransformInputGuardrail.class })
        String transformedTool(String input) {
            return "Processed: " + input;
        }

        @Tool
        @ToolOutputGuardrails({ FilterOutputGuardrail.class })
        String filteredTool(String input) {
            return "Processed: " + input;
        }
    }

    @ApplicationScoped
    public static class ValidationInputGuardrail implements ToolInputGuardrail {
        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            return ToolInputGuardrailResult.success();
        }
    }

    @ApplicationScoped
    public static class TransformInputGuardrail implements ToolInputGuardrail {
        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            return ToolInputGuardrailResult.success();
        }
    }

    @ApplicationScoped
    public static class FilterOutputGuardrail implements ToolOutputGuardrail {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            return ToolOutputGuardrailResult.success();
        }
    }
}
