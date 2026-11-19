package io.quarkiverse.langchain4j.agentic.runtime.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.internal.A2AClientBuilder;
import dev.langchain4j.agentic.observability.A2AStreamingClientListener;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.scope.AgenticScope;

class AuthenticatingA2AClientBuilderTest {

    @Test
    void forwardsTheOptionsWithDefaultImplementations() {
        RecordingBuilder delegate = new RecordingBuilder();
        Supplier<Map<String, String>> cardHeaders = () -> Map.of("X-Card", "value");

        A2AClientBuilder<Object> builder = new AuthenticatingA2AClientBuilder<>(delegate, null)
                .name("remote")
                .description("A remote agent")
                .tenant("tenant-1")
                .agentCardHeaders(cardHeaders)
                .resolutionTimeout(Duration.ofSeconds(5));

        assertEquals("remote", delegate.calls.get("name"));
        assertEquals("A remote agent", delegate.calls.get("description"));
        assertEquals("tenant-1", delegate.calls.get("tenant"));
        assertSame(cardHeaders, delegate.calls.get("agentCardHeaders"));
        assertEquals(Duration.ofSeconds(5), delegate.calls.get("resolutionTimeout"));
        assertEquals(5, delegate.calls.size() - 1, "only the five options and the auth customizer: " + delegate.calls);
        assertSame(delegate.built, builder.build());
    }

    private static final class RecordingBuilder implements A2AClientBuilder<Object> {

        final Map<String, Object> calls = new HashMap<>();
        final Object built = new Object();

        @Override
        public A2AClientBuilder<Object> name(String name) {
            calls.put("name", name);
            return this;
        }

        @Override
        public A2AClientBuilder<Object> description(String description) {
            calls.put("description", description);
            return this;
        }

        @Override
        public A2AClientBuilder<Object> tenant(String tenant) {
            calls.put("tenant", tenant);
            return this;
        }

        @Override
        public A2AClientBuilder<Object> agentCardHeaders(Supplier<Map<String, String>> headers) {
            calls.put("agentCardHeaders", headers);
            return this;
        }

        @Override
        public A2AClientBuilder<Object> resolutionTimeout(Duration resolutionTimeout) {
            calls.put("resolutionTimeout", resolutionTimeout);
            return this;
        }

        @Override
        public A2AClientBuilder<Object> clientCustomizer(Consumer<?> clientCustomizer) {
            calls.put("clientCustomizer", clientCustomizer);
            return this;
        }

        @Override
        public A2AClientBuilder<Object> inputKeys(String... inputKeys) {
            calls.put("inputKeys", inputKeys);
            return this;
        }

        @Override
        public A2AClientBuilder<Object> outputKey(String outputKey) {
            calls.put("outputKey", outputKey);
            return this;
        }

        @Override
        public A2AClientBuilder<Object> async(boolean async) {
            calls.put("async", async);
            return this;
        }

        @Override
        public A2AClientBuilder<Object> listener(AgentListener agentListener) {
            calls.put("listener", agentListener);
            return this;
        }

        @Override
        public A2AClientBuilder<Object> activationCondition(String description,
                Predicate<AgenticScope> activationCondition) {
            calls.put("activationCondition", activationCondition);
            return this;
        }

        @Override
        public A2AClientBuilder<Object> streamingClientListener(A2AStreamingClientListener<?> clientListener) {
            calls.put("streamingClientListener", clientListener);
            return this;
        }

        @Override
        public Object build() {
            return built;
        }
    }
}
