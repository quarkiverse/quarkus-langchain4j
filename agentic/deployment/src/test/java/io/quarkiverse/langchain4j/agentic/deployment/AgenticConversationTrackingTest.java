package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.deployment.Agents.CategoryRouter;
import io.quarkiverse.langchain4j.agentic.deployment.Agents.FixedResponseChatModel;
import io.quarkiverse.langchain4j.agentic.deployment.Agents.LegalExpert;
import io.quarkiverse.langchain4j.agentic.deployment.Agents.LegalExpertWithMemory;
import io.quarkiverse.langchain4j.agentic.deployment.Agents.MedicalExpert;
import io.quarkiverse.langchain4j.agentic.deployment.Agents.MedicalExpertWithMemory;
import io.quarkiverse.langchain4j.agentic.deployment.Agents.RequestCategory;
import io.quarkiverse.langchain4j.agentic.deployment.Agents.TechnicalExpert;
import io.quarkiverse.langchain4j.agentic.deployment.Agents.TechnicalExpertWithMemory;
import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatScopeConversationBridge;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.runtime.conversation.ConversationContext;
import io.quarkiverse.langchain4j.runtime.conversation.ConversationEnded;
import io.quarkiverse.langchain4j.runtime.conversation.ConversationStarted;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.common.vertx.VertxContext;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

public class AgenticConversationTrackingTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(FixedResponseChatModel.class,
                            CategoryRouter.class,
                            MedicalExpert.class,
                            TechnicalExpert.class,
                            LegalExpert.class,
                            MedicalExpertWithMemory.class,
                            TechnicalExpertWithMemory.class,
                            LegalExpertWithMemory.class,
                            RequestCategory.class,
                            ExpertsAgent.class,
                            ExpertRouterAgent.class,
                            ExpertsAgentWithMemory.class,
                            ExpertRouterAgentWithMemory.class,
                            ChatScopeConversationBridge.class,
                            ConversationEventCollector.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    public interface ExpertsAgent {

        @ConditionalAgent(outputKey = "response", subAgents = { MedicalExpert.class, TechnicalExpert.class, LegalExpert.class })
        String askExpert(@V("request") String request);

        @ActivationCondition(MedicalExpert.class)
        static boolean activateMedical(@V("category") RequestCategory category) {
            return category == RequestCategory.MEDICAL;
        }

        @ActivationCondition(TechnicalExpert.class)
        static boolean activateTechnical(@V("category") RequestCategory category) {
            return category == RequestCategory.TECHNICAL;
        }

        @ActivationCondition(LegalExpert.class)
        static boolean activateLegal(@V("category") RequestCategory category) {
            return category == RequestCategory.LEGAL;
        }
    }

    public interface ExpertRouterAgent {

        @SequenceAgent(outputKey = "response", subAgents = { CategoryRouter.class, ExpertsAgent.class })
        ResultWithAgenticScope<String> ask(@V("request") String request);
    }

    public interface ExpertsAgentWithMemory {

        @ConditionalAgent(outputKey = "response", subAgents = { MedicalExpertWithMemory.class,
                TechnicalExpertWithMemory.class, LegalExpertWithMemory.class })
        String askExpert(@V("request") String request);

        @ActivationCondition(MedicalExpertWithMemory.class)
        static boolean activateMedical(@V("category") RequestCategory category) {
            return category == RequestCategory.MEDICAL;
        }

        @ActivationCondition(TechnicalExpertWithMemory.class)
        static boolean activateTechnical(@V("category") RequestCategory category) {
            return category == RequestCategory.TECHNICAL;
        }

        @ActivationCondition(LegalExpertWithMemory.class)
        static boolean activateLegal(@V("category") RequestCategory category) {
            return category == RequestCategory.LEGAL;
        }
    }

    public interface ExpertRouterAgentWithMemory extends AgenticScopeAccess {

        @SequenceAgent(outputKey = "response", subAgents = { CategoryRouter.class, ExpertsAgentWithMemory.class })
        String ask(@MemoryId String memoryId, @V("request") String request);
    }

    @Inject
    ExpertRouterAgent expertRouterAgent;

    @Inject
    ExpertRouterAgentWithMemory expertRouterAgentWithMemory;

    @Inject
    ConversationEventCollector collector;

    @Inject
    Vertx vertx;

    @BeforeEach
    void reset() {
        collector.clear();
    }

    @Test
    @RunOnVertxContext(runOnEventLoop = false)
    void conversationsAreTrackedDuringAgenticWorkflow(UniAsserter asserter) {
        asserter.execute(() -> {
            // First conversation — medical question
            ChatScope.begin();
            String conv1 = ChatScope.id();
            assertThat(ConversationContext.isActive()).isTrue();
            assertThat(ConversationContext.current()).isEqualTo(conv1);

            ResultWithAgenticScope<String> result1 = expertRouterAgent.ask("I broke my leg, what should I do?");
            assertThat(result1.agenticScope().readState("category")).isEqualTo(RequestCategory.MEDICAL);
            assertThat(ConversationContext.current()).isEqualTo(conv1);

            ChatScope.end();
            assertThat(ConversationContext.isActive()).isFalse();

            // Second conversation — technical question
            ChatScope.begin();
            String conv2 = ChatScope.id();
            assertThat(conv2).isNotEqualTo(conv1);
            assertThat(ConversationContext.current()).isEqualTo(conv2);

            ResultWithAgenticScope<String> result2 = expertRouterAgent.ask("My computer has liquid inside, what should I do?");
            assertThat(result2.agenticScope().readState("category")).isEqualTo(RequestCategory.TECHNICAL);
            assertThat(ConversationContext.current()).isEqualTo(conv2);

            ChatScope.end();
            assertThat(ConversationContext.isActive()).isFalse();

            // Both conversations tracked with correct lifecycle events
            assertThat(collector.started()).containsExactly(conv1, conv2);
            assertThat(collector.ended()).containsExactly(conv1, conv2);
        });
    }

    @Test
    @RunOnVertxContext(runOnEventLoop = false)
    void multiInteractionConversationsAreTrackedWithMemory(UniAsserter asserter) {
        asserter.execute(() -> {
            // First conversation — medical then legal follow-up (2 interactions)
            ChatScope.begin();
            String conv1 = ChatScope.id();

            String response1 = expertRouterAgentWithMemory.ask(conv1, "I broke my leg, what should I do?");
            assertThat(expertRouterAgentWithMemory.getAgenticScope(conv1)
                    .readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.MEDICAL);
            assertThat(ConversationContext.current()).isEqualTo(conv1);

            String legalResponse1 = expertRouterAgentWithMemory.ask(conv1,
                    "Should I sue my neighbor who caused this damage?");
            assertThat(expertRouterAgentWithMemory.getAgenticScope(conv1)
                    .readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.LEGAL);
            assertThat(legalResponse1).containsIgnoringCase("leg").doesNotContain("computer");
            assertThat(ConversationContext.current()).isEqualTo(conv1);

            ChatScope.end();
            assertThat(ConversationContext.isActive()).isFalse();

            // Second conversation — technical then legal follow-up (2 interactions)
            ChatScope.begin();
            String conv2 = ChatScope.id();
            assertThat(conv2).isNotEqualTo(conv1);

            String response2 = expertRouterAgentWithMemory.ask(conv2,
                    "My computer has liquid inside, what should I do?");
            assertThat(expertRouterAgentWithMemory.getAgenticScope(conv2)
                    .readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.TECHNICAL);
            assertThat(ConversationContext.current()).isEqualTo(conv2);

            String legalResponse2 = expertRouterAgentWithMemory.ask(conv2,
                    "Should I sue my neighbor who caused this damage?");
            assertThat(expertRouterAgentWithMemory.getAgenticScope(conv2)
                    .readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.LEGAL);
            assertThat(legalResponse2).containsIgnoringCase("computer").doesNotContain("leg");
            assertThat(ConversationContext.current()).isEqualTo(conv2);

            ChatScope.end();
            assertThat(ConversationContext.isActive()).isFalse();

            // Both conversations tracked — each had 2 interactions but only 1 begin/end per scope
            assertThat(collector.started()).containsExactly(conv1, conv2);
            assertThat(collector.ended()).containsExactly(conv1, conv2);

            assertThat(expertRouterAgentWithMemory.evictAgenticScope(conv1)).isTrue();
            assertThat(expertRouterAgentWithMemory.evictAgenticScope(conv2)).isTrue();
            assertThat(expertRouterAgentWithMemory.evictAgenticScope(conv1)).isFalse();
            assertThat(expertRouterAgentWithMemory.evictAgenticScope(conv2)).isFalse();
        });
    }

    @Test
    @RunOnVertxContext(runOnEventLoop = false)
    void conversationsAreTrackedWithConversationContext(UniAsserter asserter) {
        asserter.execute(() -> {
            // First conversation — medical question
            ConversationContext.begin("conv-1");
            assertThat(ConversationContext.isActive()).isTrue();
            assertThat(ConversationContext.current()).isEqualTo("conv-1");

            ResultWithAgenticScope<String> result1 = expertRouterAgent.ask("I broke my leg, what should I do?");
            assertThat(result1.agenticScope().readState("category")).isEqualTo(RequestCategory.MEDICAL);
            assertThat(ConversationContext.current()).isEqualTo("conv-1");

            ConversationContext.end();
            assertThat(ConversationContext.isActive()).isFalse();

            // Second conversation — technical question
            ConversationContext.begin("conv-2");
            assertThat(ConversationContext.current()).isEqualTo("conv-2");

            ResultWithAgenticScope<String> result2 = expertRouterAgent.ask("My computer has liquid inside, what should I do?");
            assertThat(result2.agenticScope().readState("category")).isEqualTo(RequestCategory.TECHNICAL);
            assertThat(ConversationContext.current()).isEqualTo("conv-2");

            ConversationContext.end();
            assertThat(ConversationContext.isActive()).isFalse();

            // Both conversations tracked with correct lifecycle events
            assertThat(collector.started()).containsExactly("conv-1", "conv-2");
            assertThat(collector.ended()).containsExactly("conv-1", "conv-2");
        });
    }

    @Test
    @RunOnVertxContext(runOnEventLoop = false)
    void multiInteractionConversationsAreTrackedWithConversationContext(UniAsserter asserter) {
        asserter.execute(() -> {
            // First conversation — medical then legal follow-up (2 interactions)
            ConversationContext.begin("conv-1");

            String response1 = expertRouterAgentWithMemory.ask("conv-1", "I broke my leg, what should I do?");
            assertThat(expertRouterAgentWithMemory.getAgenticScope("conv-1")
                    .readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.MEDICAL);
            assertThat(ConversationContext.current()).isEqualTo("conv-1");

            String legalResponse1 = expertRouterAgentWithMemory.ask("conv-1",
                    "Should I sue my neighbor who caused this damage?");
            assertThat(expertRouterAgentWithMemory.getAgenticScope("conv-1")
                    .readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.LEGAL);
            assertThat(legalResponse1).containsIgnoringCase("leg").doesNotContain("computer");
            assertThat(ConversationContext.current()).isEqualTo("conv-1");

            ConversationContext.end();
            assertThat(ConversationContext.isActive()).isFalse();

            // Second conversation — technical then legal follow-up (2 interactions)
            ConversationContext.begin("conv-2");

            String response2 = expertRouterAgentWithMemory.ask("conv-2",
                    "My computer has liquid inside, what should I do?");
            assertThat(expertRouterAgentWithMemory.getAgenticScope("conv-2")
                    .readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.TECHNICAL);
            assertThat(ConversationContext.current()).isEqualTo("conv-2");

            String legalResponse2 = expertRouterAgentWithMemory.ask("conv-2",
                    "Should I sue my neighbor who caused this damage?");
            assertThat(expertRouterAgentWithMemory.getAgenticScope("conv-2")
                    .readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.LEGAL);
            assertThat(legalResponse2).containsIgnoringCase("computer").doesNotContain("leg");
            assertThat(ConversationContext.current()).isEqualTo("conv-2");

            ConversationContext.end();
            assertThat(ConversationContext.isActive()).isFalse();

            // Both conversations tracked — each had 2 interactions but only 1 begin/end per conversation
            assertThat(collector.started()).containsExactly("conv-1", "conv-2");
            assertThat(collector.ended()).containsExactly("conv-1", "conv-2");

            assertThat(expertRouterAgentWithMemory.evictAgenticScope("conv-1")).isTrue();
            assertThat(expertRouterAgentWithMemory.evictAgenticScope("conv-2")).isTrue();
            assertThat(expertRouterAgentWithMemory.evictAgenticScope("conv-1")).isFalse();
            assertThat(expertRouterAgentWithMemory.evictAgenticScope("conv-2")).isFalse();
        });
    }

    @Test
    void parallelConversationsAreIsolated() throws Exception {
        Context dc1 = VertxContext.createNewDuplicatedContext(vertx.getOrCreateContext());
        Context dc2 = VertxContext.createNewDuplicatedContext(vertx.getOrCreateContext());

        CompletableFuture<String> future1 = new CompletableFuture<>();
        CompletableFuture<String> future2 = new CompletableFuture<>();

        dc1.executeBlocking(() -> {
            ConversationContext.begin("conv-1");
            assertThat(ConversationContext.current()).isEqualTo("conv-1");

            expertRouterAgentWithMemory.ask("conv-1", "I broke my leg, what should I do?");
            assertThat(expertRouterAgentWithMemory.getAgenticScope("conv-1")
                    .readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.MEDICAL);
            assertThat(ConversationContext.current()).isEqualTo("conv-1");

            String legalResponse = expertRouterAgentWithMemory.ask("conv-1",
                    "Should I sue my neighbor who caused this damage?");
            assertThat(expertRouterAgentWithMemory.getAgenticScope("conv-1")
                    .readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.LEGAL);
            assertThat(ConversationContext.current()).isEqualTo("conv-1");

            ConversationContext.end();
            assertThat(ConversationContext.isActive()).isFalse();
            return legalResponse;
        }).onComplete(ar -> {
            if (ar.failed())
                future1.completeExceptionally(ar.cause());
            else
                future1.complete(ar.result());
        });

        dc2.executeBlocking(() -> {
            ConversationContext.begin("conv-2");
            assertThat(ConversationContext.current()).isEqualTo("conv-2");

            expertRouterAgentWithMemory.ask("conv-2", "My computer has liquid inside, what should I do?");
            assertThat(expertRouterAgentWithMemory.getAgenticScope("conv-2")
                    .readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.TECHNICAL);
            assertThat(ConversationContext.current()).isEqualTo("conv-2");

            String legalResponse = expertRouterAgentWithMemory.ask("conv-2",
                    "Should I sue my neighbor who caused this damage?");
            assertThat(expertRouterAgentWithMemory.getAgenticScope("conv-2")
                    .readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.LEGAL);
            assertThat(ConversationContext.current()).isEqualTo("conv-2");

            ConversationContext.end();
            assertThat(ConversationContext.isActive()).isFalse();
            return legalResponse;
        }).onComplete(ar -> {
            if (ar.failed())
                future2.completeExceptionally(ar.cause());
            else
                future2.complete(ar.result());
        });

        CompletableFuture.allOf(future1, future2).get(30, TimeUnit.SECONDS);

        // Memory isolation: each conversation's legal follow-up recalls its own prior context
        assertThat(future1.get()).containsIgnoringCase("leg").doesNotContain("computer");
        assertThat(future2.get()).containsIgnoringCase("computer").doesNotContain("leg");

        assertThat(collector.started()).containsExactlyInAnyOrder("conv-1", "conv-2");
        assertThat(collector.ended()).containsExactlyInAnyOrder("conv-1", "conv-2");

        assertThat(expertRouterAgentWithMemory.evictAgenticScope("conv-1")).isTrue();
        assertThat(expertRouterAgentWithMemory.evictAgenticScope("conv-2")).isTrue();
    }

    @ApplicationScoped
    public static class ConversationEventCollector {
        private final List<String> started = new CopyOnWriteArrayList<>();
        private final List<String> ended = new CopyOnWriteArrayList<>();

        public void onStarted(@Observes ConversationStarted event) {
            started.add(event.getConversationId());
        }

        public void onEnded(@Observes ConversationEnded event) {
            ended.add(event.getConversationId());
        }

        public List<String> started() {
            return started;
        }

        public List<String> ended() {
            return ended;
        }

        public void clear() {
            started.clear();
            ended.clear();
        }
    }
}
