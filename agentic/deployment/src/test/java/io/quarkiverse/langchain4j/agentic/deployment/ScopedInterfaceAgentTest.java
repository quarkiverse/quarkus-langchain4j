package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies that a scope annotation on an agent interface is honoured: each bean instance has its own agentic scope,
 * evicted with the chat memories created for it when the instance is destroyed.
 */
public class ScopedInterfaceAgentTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @ApplicationScoped
    public static class MemoryStore {
        private final Map<Object, ChatMemory> memories = new ConcurrentHashMap<>();

        public ChatMemory memoryOf(Object memoryId) {
            return memories.computeIfAbsent(memoryId, id -> MessageWindowChatMemory.builder().id(id).maxMessages(10)
                    .build());
        }

        public Map<Object, ChatMemory> memories() {
            return memories;
        }
    }

    public interface Echo {

        @UserMessage("Echo {{text}}")
        @Agent(description = "Echoes the text", outputKey = "echo")
        String echo(@V("text") String text);

        @ChatModelSupplier
        static ChatModel model() {
            return new EchoModel();
        }

        @ChatMemoryProviderSupplier
        static ChatMemory memory(Object memoryId, @CdiBean MemoryStore store) {
            return store.memoryOf(memoryId);
        }
    }

    /** Replies with the last user message. */
    public static class EchoModel implements ChatModel {
        @Override
        public dev.langchain4j.model.chat.response.ChatResponse doChat(
                dev.langchain4j.model.chat.request.ChatRequest request) {
            var last = (dev.langchain4j.data.message.UserMessage) request.messages().get(request.messages().size() - 1);
            return dev.langchain4j.model.chat.response.ChatResponse.builder()
                    .aiMessage(new dev.langchain4j.data.message.AiMessage(last.singleText())).build();
        }
    }

    @RequestScoped
    public interface EchoSequence {

        AtomicReference<Object> LAST_MEMORY_ID = new AtomicReference<>();

        @SequenceAgent(outputKey = "echo", subAgents = Echo.class)
        String echo(@V("text") String text);

        @Output
        static String output(AgenticScope scope) {
            LAST_MEMORY_ID.set(scope.memoryId());
            return scope.readState("echo", "");
        }
    }

    @Inject
    EchoSequence echoSequence;

    @Inject
    MemoryStore memoryStore;

    @Test
    void requestScopedInterfaceAgent() {
        ManagedContext requestContext = Arc.container().requestContext();

        requestContext.activate();
        Object firstMemoryId;
        AgenticScopeAccess firstInstance;
        try {
            assertThat(echoSequence.echo("one")).isEqualTo("Echo one");
            firstMemoryId = EchoSequence.LAST_MEMORY_ID.get();
            firstInstance = (AgenticScopeAccess) ClientProxy.unwrap(echoSequence);
            assertThat(firstInstance.getAgenticScope(firstMemoryId)).isNotNull();
            // the chat memory of the sub-agent defaults to the memory id of the instance
            assertThat(memoryStore.memories().keySet()).anyMatch(id -> id.toString().startsWith(firstMemoryId + "#"));
            echoSequence.echo("two");
            assertThat(EchoSequence.LAST_MEMORY_ID.get()).isEqualTo(firstMemoryId);
        } finally {
            requestContext.terminate();
        }
        // the agentic scope and the chat memories of the instance are evicted with it
        assertThat(firstInstance.getAgenticScope(firstMemoryId)).isNull();
        memoryStore.memories().forEach((id, memory) -> {
            if (id.toString().startsWith(firstMemoryId + "#")) {
                assertThat(memory.messages()).isEmpty();
            }
        });

        requestContext.activate();
        try {
            assertThat(echoSequence.echo("three")).isEqualTo("Echo three");
            assertThat(EchoSequence.LAST_MEMORY_ID.get()).isNotEqualTo(firstMemoryId);
            assertThat(ClientProxy.unwrap(echoSequence)).isNotSameAs(firstInstance);
        } finally {
            requestContext.terminate();
        }
    }
}
