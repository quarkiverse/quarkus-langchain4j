package io.quarkiverse.langchain4j.agentic.runtime.devui;

import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class AgenticJsonRpcServiceAllowListTest {

    private AgenticJsonRpcService service;

    @BeforeEach
    void setUp() {
        service = new AgenticJsonRpcService();
        DevAgentMonitorHolder.allowedAgentClassNames = Collections.emptySet();
    }

    @AfterEach
    void tearDown() {
        DevAgentMonitorHolder.allowedAgentClassNames = Collections.emptySet();
    }

    @Test
    void rejectsClassNotInAllowList() {
        DevAgentMonitorHolder.allowedAgentClassNames = Set.of("com.example.AllowedAgent");

        JsonObject result = service.invokeAgent("com.example.OtherClass", "chat", null);

        Assertions.assertFalse(result.getBoolean("success"));
        Assertions.assertEquals("Unknown agent class: com.example.OtherClass", result.getString("error"));
    }

    @Test
    void rejectsAllClassesWhenAllowListIsEmpty() {
        JsonObject result = service.invokeAgent("java.lang.String", "valueOf", null);

        Assertions.assertFalse(result.getBoolean("success"));
        Assertions.assertEquals("Unknown agent class: java.lang.String", result.getString("error"));
    }

    @Test
    void passesGuardForAllowedClass() {
        // Use a real classpath class so Class.forName succeeds — CDI will be absent in
        // this unit test context but the guard itself must not reject the call.
        DevAgentMonitorHolder.allowedAgentClassNames = Set.of("java.lang.String");

        JsonObject result = service.invokeAgent("java.lang.String", "valueOf", null);

        Assertions.assertFalse(result.getBoolean("success"));
        Assertions.assertFalse(result.getString("error").startsWith("Unknown agent class:"),
                "Guard should be bypassed for an allowed class; got: " + result.getString("error"));
    }
}
