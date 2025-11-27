package io.quarkiverse.langchain4j.runtime.tool.guardrails;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrailResult;

class ToolOutputGuardrailResultTest {

    @Test
    void testSuccess() {
        ToolOutputGuardrailResult result = ToolOutputGuardrailResult.success();

        assertTrue(result.isSuccess());
        assertNull(result.modifiedResult());
        assertNull(result.errorMessage());
        assertNull(result.cause());
    }

    @Test
    void testSuccessWith() {
        ToolExecutionResult modifiedResult = ToolExecutionResult.builder()
                .resultText("Modified output")
                .build();

        ToolOutputGuardrailResult result = ToolOutputGuardrailResult.successWith(modifiedResult);

        assertTrue(result.isSuccess());
        assertSame(modifiedResult, result.modifiedResult());
        assertNull(result.errorMessage());
        assertNull(result.cause());
    }

    @Test
    void testFailure_withMessage() {
        ToolOutputGuardrailResult result = ToolOutputGuardrailResult.failure("Validation failed");

        assertFalse(result.isSuccess());
        assertNull(result.modifiedResult());
        assertEquals("Validation failed", result.errorMessage());
        assertNull(result.cause());
    }

    @Test
    void testFailure_withMessageAndCause() {
        Exception cause = new RuntimeException("Root cause");
        ToolOutputGuardrailResult result = ToolOutputGuardrailResult.failure("Validation failed", cause);

        assertFalse(result.isSuccess());
        assertNull(result.modifiedResult());
        assertEquals("Validation failed", result.errorMessage());
        assertSame(cause, result.cause());
    }
}
