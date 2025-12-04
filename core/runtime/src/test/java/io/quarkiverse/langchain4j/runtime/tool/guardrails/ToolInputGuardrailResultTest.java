package io.quarkiverse.langchain4j.runtime.tool.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailResult;

class ToolInputGuardrailResultTest {

    @Test
    void testSuccess() {
        ToolInputGuardrailResult result = ToolInputGuardrailResult.success();

        assertTrue(result.isSuccess());
        assertNull(result.modifiedRequest());
        assertNull(result.errorMessage());
        assertNull(result.cause());
    }

    @Test
    void testSuccessWith() {
        ToolExecutionRequest modifiedRequest = ToolExecutionRequest.builder()
                .id("modified-id")
                .name("modifiedTool")
                .arguments("{\"modified\": true}")
                .build();

        ToolInputGuardrailResult result = ToolInputGuardrailResult.successWith(modifiedRequest);

        assertTrue(result.isSuccess());
        assertSame(modifiedRequest, result.modifiedRequest());
        assertNull(result.errorMessage());
        assertNull(result.cause());
    }

    @Test
    void testFailure_withMessage() {
        ToolInputGuardrailResult result = ToolInputGuardrailResult.failure("Validation failed");

        assertFalse(result.isSuccess());
        assertNull(result.modifiedRequest());
        assertEquals("Validation failed", result.errorMessage());
        assertNull(result.cause());
    }

    @Test
    void testFatal_withMessageAndCause() {
        Exception cause = new RuntimeException("Root cause");
        ToolInputGuardrailResult result = ToolInputGuardrailResult.fatal("Validation failed", cause);

        assertFalse(result.isSuccess());
        assertTrue(result.isFatalFailure());
        assertNull(result.modifiedRequest());
        assertEquals("Validation failed", result.errorMessage());
        assertSame(cause, result.cause());
    }
}
