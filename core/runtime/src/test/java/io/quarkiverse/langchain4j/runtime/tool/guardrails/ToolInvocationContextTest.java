package io.quarkiverse.langchain4j.runtime.tool.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import io.quarkiverse.langchain4j.guardrails.ToolInvocationContext;

class ToolInvocationContextTest {

    @Test
    void testConstruction_withNullParameters() {
        ToolInvocationContext context = new ToolInvocationContext(
                InvocationContext.builder().chatMemoryId("memory-123").build());

        assertEquals("memory-123", context.memoryId());
        assertNotNull(context.parameters());
        assertTrue(context.parameters().isEmpty());
    }

    @Test
    void testConstruction_withParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("user", "alice");
        params.put("tenant", "acme");

        ToolInvocationContext context = new ToolInvocationContext(InvocationContext.builder().chatMemoryId("memory-123")
                .invocationParameters(InvocationParameters.from(params))
                .build());

        assertEquals("memory-123", context.memoryId());
        assertEquals(2, context.parameters().size());
        assertEquals("alice", context.parameters().get("user"));
        assertEquals("acme", context.parameters().get("tenant"));
    }

    @Test
    void testParameters_immutable() {
        Map<String, Object> params = new HashMap<>();
        params.put("user", "alice");

        ToolInvocationContext context = new ToolInvocationContext(InvocationContext.builder().chatMemoryId("memory-123")
                .invocationParameters(InvocationParameters.from(params))
                .build());

        // Original map can be modified
        params.put("user", "bob");

        // But context parameters remain unchanged
        assertEquals("alice", context.parameters().get("user"));

        // Cannot modify context parameters
        assertThrows(UnsupportedOperationException.class,
                () -> context.parameters().put("user", "charlie"));
    }

    @Test
    void testParameter() {
        Map<String, Object> params = Map.of(
                "user", "alice",
                "session", "session-456");

        ToolInvocationContext context = new ToolInvocationContext(InvocationContext.builder().chatMemoryId("memory-123")
                .invocationParameters(InvocationParameters.from(params))
                .build());

        assertEquals("alice", context.parameter("user"));
        assertEquals("session-456", context.parameter("session"));
        assertNull(context.parameter("nonexistent"));
    }

    @Test
    void testHasParameter() {
        Map<String, Object> params = Map.of("user", "alice");

        ToolInvocationContext context = new ToolInvocationContext(InvocationContext.builder().chatMemoryId("memory-123")
                .invocationParameters(InvocationParameters.from(params))
                .build());

        assertTrue(context.hasParameter("user"));
        assertFalse(context.hasParameter("nonexistent"));
    }

    @Test
    void testEmptyContext() {
        ToolInvocationContext context = new ToolInvocationContext(InvocationContext.builder().build());

        assertNull(context.memoryId());
        assertNotNull(context.parameters());
        assertTrue(context.parameters().isEmpty());
        assertFalse(context.hasParameter("anything"));
        assertNull(context.parameter("anything"));
    }
}
