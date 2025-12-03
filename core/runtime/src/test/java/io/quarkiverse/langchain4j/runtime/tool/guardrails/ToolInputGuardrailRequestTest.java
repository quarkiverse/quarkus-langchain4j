package io.quarkiverse.langchain4j.runtime.tool.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolInvocationContext;
import io.quarkiverse.langchain4j.guardrails.ToolMetadata;

class ToolInputGuardrailRequestTest {

    @Test
    void testConstruction() {
        ToolExecutionRequest executionRequest = createToolExecutionRequest();
        ToolMetadata toolMetadata = createToolMetadata();
        ToolInvocationContext invocationContext = new ToolInvocationContext(
                InvocationContext.builder().chatMemoryId("memory-123")
                        .invocationParameters(InvocationParameters.from("user", "alice")).build());

        ToolInputGuardrailRequest request = new ToolInputGuardrailRequest(
                executionRequest, toolMetadata, invocationContext);

        assertSame(executionRequest, request.executionRequest());
        assertSame(toolMetadata, request.toolMetadata());
        assertSame(invocationContext, request.invocationContext());
    }

    @Test
    void testConstruction_nullExecutionRequest() {
        assertThrows(IllegalArgumentException.class,
                () -> new ToolInvocationContext(null));
    }

    @Test
    void testConstruction_nullMetadataAndContext() {
        // Metadata and context can be null
        ToolExecutionRequest executionRequest = createToolExecutionRequest();

        ToolInputGuardrailRequest request = new ToolInputGuardrailRequest(executionRequest, null, null);

        assertSame(executionRequest, request.executionRequest());
        assertNull(request.toolMetadata());
        assertNull(request.invocationContext());
    }

    @Test
    void testToolName() {
        ToolExecutionRequest executionRequest = createToolExecutionRequest();
        ToolInputGuardrailRequest request = new ToolInputGuardrailRequest(executionRequest, null, null);

        assertEquals("testTool", request.toolName());
    }

    @Test
    void testArguments() {
        ToolExecutionRequest executionRequest = createToolExecutionRequest();
        ToolInputGuardrailRequest request = new ToolInputGuardrailRequest(executionRequest, null, null);

        assertEquals("{\"param\": \"value\"}", request.arguments());
    }

    @Test
    void testMemoryId() {
        ToolExecutionRequest executionRequest = createToolExecutionRequest();
        ToolInvocationContext invocationContext = new ToolInvocationContext(
                InvocationContext.builder().chatMemoryId("memory-123")
                        .invocationParameters(InvocationParameters.from("user", "alice")).build());
        ToolInputGuardrailRequest request = new ToolInputGuardrailRequest(executionRequest, null, invocationContext);

        assertEquals("memory-123", request.memoryId());
    }

    @Test
    void testMemoryId_nullContext() {
        ToolExecutionRequest executionRequest = createToolExecutionRequest();
        ToolInputGuardrailRequest request = new ToolInputGuardrailRequest(executionRequest, null, null);

        assertNull(request.memoryId());
    }

    private ToolExecutionRequest createToolExecutionRequest() {
        return ToolExecutionRequest.builder()
                .id("test-id")
                .name("testTool")
                .arguments("{\"param\": \"value\"}")
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
