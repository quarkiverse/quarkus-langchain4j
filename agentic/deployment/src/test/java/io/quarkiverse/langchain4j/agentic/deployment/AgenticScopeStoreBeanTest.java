package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.AgenticScopeKey;
import dev.langchain4j.agentic.scope.AgenticScopeSerializer;
import dev.langchain4j.agentic.scope.AgenticScopeStore;
import dev.langchain4j.agentic.scope.AgenticSystemSuspendedException;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * An {@link AgenticScopeStore} bean is installed without calling {@code AgenticScopePersister.setStore(...)}: a workflow
 * that suspends on a human-in-the-loop step survives the loss of its in-memory state and resumes from the store.
 * Modelled on {@code RecoverabilityIT} in langchain4j-agentic.
 */
public class AgenticScopeStoreBeanTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(InMemoryJsonAgenticScopeStore.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true");

    public interface RecoverableWorkflow extends AgenticScopeAccess {
        @Agent
        String process(@MemoryId String sessionId, @V("input") String input);
    }

    @ApplicationScoped
    public static class InMemoryJsonAgenticScopeStore implements AgenticScopeStore {

        final Map<AgenticScopeKey, String> scopes = new ConcurrentHashMap<>();
        final AtomicInteger loads = new AtomicInteger();

        @Override
        public boolean save(AgenticScopeKey key, DefaultAgenticScope agenticScope) {
            scopes.put(key, AgenticScopeSerializer.toJson(agenticScope));
            return true;
        }

        @Override
        public Optional<DefaultAgenticScope> load(AgenticScopeKey key) {
            loads.incrementAndGet();
            return Optional.ofNullable(scopes.get(key)).map(AgenticScopeSerializer::fromJson);
        }

        @Override
        public boolean delete(AgenticScopeKey key) {
            return scopes.remove(key) != null;
        }

        @Override
        public Set<AgenticScopeKey> getAllKeys() {
            return Set.copyOf(scopes.keySet());
        }

        int loadCount() {
            return loads.get();
        }
    }

    @Inject
    InMemoryJsonAgenticScopeStore store;

    @Test
    void workflowResumesFromStoreBeanAfterLosingInMemoryState() {
        AtomicInteger processorRuns = new AtomicInteger();
        RecoverableWorkflow workflow = buildWorkflow(processorRuns);

        AgenticSystemSuspendedException suspended = assertThrows(AgenticSystemSuspendedException.class,
                () -> workflow.process("session-1", "raw data"));
        assertThat(suspended.scope().pendingResponseIds()).containsExactly("human-review");
        assertThat(store.getAllKeys()).isNotEmpty();

        // simulate a crash: the store is the only copy left
        ((AgenticScopeOwner) workflow).registry().clearInMemory();

        AgenticScope recovered = workflow.getAgenticScope("session-1");
        assertThat(store.loadCount()).isPositive();
        assertThat(recovered.readState("processed_data", "")).isEqualTo("PROCESSED: raw data");
        assertThat(recovered.completePendingResponse("human-review", "APPROVED")).isTrue();

        assertThat(workflow.process("session-1", "raw data"))
                .isEqualTo("PROCESSED: raw data | APPROVED");
        // the first step is not run again after the restart
        assertThat(processorRuns.get()).isEqualTo(1);
    }

    private static RecoverableWorkflow buildWorkflow(AtomicInteger processorRuns) {
        AgenticServices.AgenticScopeAction dataProcessor = AgenticServices.agentAction(scope -> {
            processorRuns.incrementAndGet();
            scope.writeState("processed_data", "PROCESSED: " + scope.readState("input"));
        });

        HumanInTheLoop humanReviewer = AgenticServices.humanInTheLoopBuilder()
                .description("Request human approval for the processed data")
                .outputKey("approval")
                .responseProvider(scope -> new SuspendedResponse<>("human-review"))
                .build();

        AgenticServices.AgenticScopeAction finalizer = AgenticServices.agentAction(
                scope -> scope.writeState("final_result",
                        scope.readState("processed_data") + " | " + scope.readState("approval")));

        return AgenticServices.sequenceBuilder(RecoverableWorkflow.class)
                .subAgents(dataProcessor, humanReviewer, finalizer)
                .outputKey("final_result")
                .build();
    }
}
