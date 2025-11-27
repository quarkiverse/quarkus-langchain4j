package io.quarkiverse.langchain4j.runtime.tool.guardrails;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolSpecification;
import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;

/**
 * Unit tests for ToolGuardrailService focusing on metadata inspection.
 * Full integration tests with actual guardrail execution are in the deployment module.
 */
class ToolGuardrailServiceTest {

    private ToolGuardrailService service;

    @BeforeEach
    void setUp() {
        service = new ToolGuardrailService();
    }

    @Test
    void testHasInputGuardrails_withGuardrails() {
        ToolInputGuardrailsLiteral inputGuardrails = new ToolInputGuardrailsLiteral(
                List.of("com.example.TestGuardrail"));
        ToolMethodCreateInfo info = createMethodCreateInfo(inputGuardrails, null);

        assertTrue(service.hasInputGuardrails(info));
    }

    @Test
    void testHasInputGuardrails_withoutGuardrails() {
        ToolMethodCreateInfo info = createMethodCreateInfo(null, null);

        assertFalse(service.hasInputGuardrails(info));
    }

    @Test
    void testHasInputGuardrails_withEmptyGuardrails() {
        ToolInputGuardrailsLiteral inputGuardrails = new ToolInputGuardrailsLiteral(List.of());
        ToolMethodCreateInfo info = createMethodCreateInfo(inputGuardrails, null);

        assertFalse(service.hasInputGuardrails(info));
    }

    @Test
    void testHasOutputGuardrails_withGuardrails() {
        ToolOutputGuardrailsLiteral outputGuardrails = new ToolOutputGuardrailsLiteral(
                List.of("com.example.TestGuardrail"));
        ToolMethodCreateInfo info = createMethodCreateInfo(null, outputGuardrails);

        assertTrue(service.hasOutputGuardrails(info));
    }

    @Test
    void testHasOutputGuardrails_withoutGuardrails() {
        ToolMethodCreateInfo info = createMethodCreateInfo(null, null);

        assertFalse(service.hasOutputGuardrails(info));
    }

    @Test
    void testHasOutputGuardrails_withEmptyGuardrails() {
        ToolOutputGuardrailsLiteral outputGuardrails = new ToolOutputGuardrailsLiteral(List.of());
        ToolMethodCreateInfo info = createMethodCreateInfo(null, outputGuardrails);

        assertFalse(service.hasOutputGuardrails(info));
    }

    @Test
    void testHasBothGuardrails() {
        ToolInputGuardrailsLiteral inputGuardrails = new ToolInputGuardrailsLiteral(
                List.of("com.example.InputGuardrail"));
        ToolOutputGuardrailsLiteral outputGuardrails = new ToolOutputGuardrailsLiteral(
                List.of("com.example.OutputGuardrail"));
        ToolMethodCreateInfo info = createMethodCreateInfo(inputGuardrails, outputGuardrails);

        assertTrue(service.hasInputGuardrails(info));
        assertTrue(service.hasOutputGuardrails(info));
    }

    private ToolMethodCreateInfo createMethodCreateInfo(
            ToolInputGuardrailsLiteral inputGuardrails,
            ToolOutputGuardrailsLiteral outputGuardrails) {
        ToolSpecification spec = ToolSpecification.builder()
                .name("testTool")
                .description("Test tool")
                .build();

        return new ToolMethodCreateInfo(
                "testMethod",
                "TestInvoker",
                spec,
                "TestMapper",
                ToolMethodCreateInfo.ExecutionModel.BLOCKING,
                null,
                inputGuardrails,
                outputGuardrails);
    }
}
