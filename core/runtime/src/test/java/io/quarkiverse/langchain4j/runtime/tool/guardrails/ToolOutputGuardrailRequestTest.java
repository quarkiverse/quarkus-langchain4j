package io.quarkiverse.langchain4j.runtime.tool.guardrails;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.guardrails.ToolInvocationContext;
import io.quarkiverse.langchain4j.guardrails.ToolMetadata;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrailRequest;

class ToolOutputGuardrailRequestTest {

    @Test
    void testConstruction() {
        ToolExecutionResult executionResult = createToolExecutionResult();
        ToolExecutionRequest executionRequest = createToolExecutionRequest();
        ToolMetadata toolMetadata = createToolMetadata();
        ToolInvocationContext context = new ToolInvocationContext(InvocationContext.builder().chatMemoryId("memory-123")
                .invocationParameters(InvocationParameters.from(Map.of("user", "alice")))
                .build());

        ToolOutputGuardrailRequest request = new ToolOutputGuardrailRequest(
                executionResult, executionRequest, toolMetadata, context);

        assertSame(executionResult, request.executionResult());
        assertSame(executionRequest, request.executionRequest());
        assertSame(toolMetadata, request.toolMetadata());
        assertSame(context, request.invocationContext());
    }

    @Test
    void testConstruction_nullExecutionResult() {
        ToolExecutionRequest executionRequest = createToolExecutionRequest();
        ToolMetadata toolMetadata = createToolMetadata();
        ToolInvocationContext context = new ToolInvocationContext(InvocationContext.builder().build());

        assertThrows(IllegalArgumentException.class,
                () -> new ToolOutputGuardrailRequest(null, executionRequest, toolMetadata, context));
    }

    @Test
    void testConstruction_nullOptionalFields() {
        // Execution request, metadata and context can be null
        ToolExecutionResult executionResult = createToolExecutionResult();

        ToolOutputGuardrailRequest request = new ToolOutputGuardrailRequest(executionResult, null, null, null);

        assertSame(executionResult, request.executionResult());
        assertNull(request.executionRequest());
        assertNull(request.toolMetadata());
        assertNull(request.invocationContext());
    }

    @Test
    void testToolName() {
        ToolExecutionResult executionResult = createToolExecutionResult();
        ToolExecutionRequest executionRequest = createToolExecutionRequest();
        ToolOutputGuardrailRequest request = new ToolOutputGuardrailRequest(executionResult, executionRequest, null, null);

        assertEquals("testTool", request.toolName());
    }

    @Test
    void testToolName_nullRequest() {
        ToolExecutionResult executionResult = createToolExecutionResult();
        ToolOutputGuardrailRequest request = new ToolOutputGuardrailRequest(executionResult, null, null, null);

        assertNull(request.toolName());
    }

    @Test
    void testResultText() {
        ToolExecutionResult executionResult = createToolExecutionResult();
        ToolOutputGuardrailRequest request = new ToolOutputGuardrailRequest(executionResult, null, null, null);

        assertEquals("Success result", request.resultText());
    }

    @Test
    void testIsError_false() {
        ToolExecutionResult executionResult = ToolExecutionResult.builder()
                .resultText("Success")
                .build();
        ToolOutputGuardrailRequest request = new ToolOutputGuardrailRequest(executionResult, null, null, null);

        assertFalse(request.isError());
    }

    @Test
    void testIsError_true() {
        ToolExecutionResult executionResult = ToolExecutionResult.builder()
                .isError(true)
                .resultText("Error occurred")
                .build();
        ToolOutputGuardrailRequest request = new ToolOutputGuardrailRequest(executionResult, null, null, null);

        assertTrue(request.isError());
    }

    @Test
    void testMemoryId() {
        ToolExecutionResult executionResult = createToolExecutionResult();
        ToolInvocationContext context = new ToolInvocationContext(InvocationContext.builder().chatMemoryId("memory-123")
                .build());
        ToolOutputGuardrailRequest request = new ToolOutputGuardrailRequest(executionResult, null, null, context);

        assertEquals("memory-123", request.memoryId());
    }

    @Test
    void testMemoryId_nullContext() {
        ToolExecutionResult executionResult = createToolExecutionResult();
        ToolOutputGuardrailRequest request = new ToolOutputGuardrailRequest(executionResult, null, null, null);

        assertNull(request.memoryId());
    }

    private ToolExecutionRequest createToolExecutionRequest() {
        return ToolExecutionRequest.builder()
                .id("test-id")
                .name("testTool")
                .arguments("{\"param\": \"value\"}")
                .build();
    }

    private ToolExecutionResult createToolExecutionResult() {
        return ToolExecutionResult.builder()
                .resultText("Success result")
                .build();
    }

    private ToolMetadata createToolMetadata() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("testTool")
                .description("Test tool")
                .build();
        return new ToolMetadata(spec, null);
    }
}
