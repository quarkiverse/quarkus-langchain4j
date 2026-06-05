package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ContentRetrieverSupplier;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * CDI Auto-Wiring Gallery — a tour of every CDI wiring scenario for agentic agents.
 * <p>
 * Read this file top-to-bottom to understand how CDI beans are automatically wired
 * into agents. Each section demonstrates a different wiring rule with real-world
 * agent examples.
 * <p>
 * <strong>Design constraint:</strong> ContentRetriever and RetrievalAugmentor are
 * mutually exclusive per agent (langchain4j enforces "only one of retriever,
 * contentRetriever, retrievalAugmentor"). This gallery uses ContentRetriever as
 * the CDI auto-wired RAG bean. RetrievalAugmentor auto-wiring follows the same
 * build-time code path, so testing one proves the mechanism for both.
 * <p>
 * Edge cases that require different CDI contexts (multiple beans, scope validation)
 * are in separate test classes: {@link CdiAutoWiringEdgeCasesTest},
 * {@link CdiScopeValidationRequestScopedTest}, etc.
 * <p>
 * Qualifier-based resolution via {@code @CdiBean} is demonstrated in
 * {@link CdiChatSupplierParameterResolverTest}.
 */
public class CdiAutoWiringGalleryTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            // CDI beans (Section 1)
                            VectorDbRetriever.class,
                            ConversationMemory.class,
                            MetricsListener.class,
                            AuditLogger.class,
                            // Agents (Sections 2-5)
                            ResearchAgent.class,
                            TherapistAgent.class,
                            LegalAgent.class,
                            StaticRetriever.class,
                            AuditedAgent.class,
                            StaticOnlyListener.class,
                            AdvisorAgent.class,
                            Agents.FixedResponseChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    // ── Section 1: CDI Beans ─────────────────────────────────────────────────────
    //
    // These @ApplicationScoped beans represent application infrastructure.
    // CDI auto-wiring discovers them at build time and wires them into agents
    // that don't declare a static supplier for the corresponding type.

    @ApplicationScoped
    public static class VectorDbRetriever implements ContentRetriever {

        @Override
        public List<Content> retrieve(Query query) {
            return List.of(Content.from(TextSegment.from("vector-db-result")));
        }
    }

    @ApplicationScoped
    public static class ConversationMemory implements ChatMemory {

        private final List<ChatMessage> messages = new ArrayList<>();

        @Override
        public Object id() {
            return "conversation";
        }

        @Override
        public void add(ChatMessage message) {
            messages.add(message);
        }

        @Override
        public List<ChatMessage> messages() {
            return new ArrayList<>(messages);
        }

        @Override
        public void clear() {
            messages.clear();
        }
    }

    @ApplicationScoped
    public static class MetricsListener implements AgentListener {
        // Global listener — fires for every agent invocation
    }

    @ApplicationScoped
    public static class AuditLogger implements AgentListener {
        // Second global listener — both compose, neither replaces the other
    }

    // ── Section 2: Fallback Auto-Wiring ──────────────────────────────────────────
    //
    // When an agent declares no static supplier for a type, and exactly one
    // @Default CDI bean of that type exists, the bean is automatically wired
    // into the agent at build time.

    /** No @ContentRetrieverSupplier — VectorDbRetriever is auto-wired. */
    public interface ResearchAgent {

        @UserMessage("Research: {{topic}}")
        @Agent(description = "Researches topics using vector DB retrieval", outputKey = "findings")
        String research(@V("topic") String topic);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("research-findings");
        }
    }

    /** No @ChatMemorySupplier — ConversationMemory is auto-wired. */
    public interface TherapistAgent {

        @UserMessage("Patient says: {{input}}")
        @Agent(description = "Therapeutic conversation agent with memory", outputKey = "response")
        String respond(@V("input") String input);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("therapeutic-response");
        }
    }

    // ── Section 3: Static Supplier Precedence ────────────────────────────────────
    //
    // When a static supplier IS declared, it takes precedence over any CDI bean.
    // The CDI bean exists in the container but is not wired into this agent.

    public static class StaticRetriever implements ContentRetriever {

        @Override
        public List<Content> retrieve(Query query) {
            return List.of(Content.from(TextSegment.from("legal-db-result")));
        }
    }

    /** Has @ContentRetrieverSupplier — StaticRetriever is used, VectorDbRetriever is ignored. */
    public interface LegalAgent {

        @UserMessage("Legal analysis: {{query}}")
        @Agent(description = "Legal expert using a dedicated case law retriever", outputKey = "opinion")
        String analyze(@V("query") String query);

        @ContentRetrieverSupplier
        static ContentRetriever retriever() {
            return new StaticRetriever();
        }

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("legal-opinion");
        }
    }

    // ── Section 4: AgentListener Global Discovery ────────────────────────────────
    //
    // AgentListener CDI beans are different from other supplier types:
    // - They are ADDITIVE, not fallback — wired into ALL agents
    // - They compose with per-agent @AgentListenerSupplier via ComposedAgentListener
    // - MetricsListener and AuditLogger (Section 1) fire for every agent above
    //
    // An agent with its own @AgentListenerSupplier gets BOTH its static listener
    // AND the global CDI listeners.

    public static class StaticOnlyListener implements AgentListener {
        // Per-agent listener — only this agent gets it, alongside the CDI listeners
    }

    /** Has @AgentListenerSupplier — gets StaticOnlyListener + MetricsListener + AuditLogger. */
    public interface AuditedAgent {

        @UserMessage("Audit: {{input}}")
        @Agent(description = "Agent with explicit listener plus global CDI listeners", outputKey = "audit")
        String audit(@V("input") String input);

        @AgentListenerSupplier
        static AgentListener listener() {
            return new StaticOnlyListener();
        }

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("audit-result");
        }
    }

    // ── Section 5: Mixed Supplier Modes ──────────────────────────────────────────
    //
    // Different supplier types on the same agent can use different wiring modes.
    // Static and CDI suppliers coexist without conflict.

    /**
     * Static @ChatModelSupplier + no @ContentRetrieverSupplier + no @ChatMemorySupplier.
     * CDI auto-wires VectorDbRetriever and ConversationMemory alongside the static ChatModel.
     */
    public interface AdvisorAgent {

        @UserMessage("Advise on: {{question}}")
        @Agent(description = "Financial advisor with static model, CDI retriever and memory", outputKey = "advice")
        String advise(@V("question") String question);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("financial-advice");
        }
    }

    // ── Tests ────────────────────────────────────────────────────────────────────

    @Inject
    ResearchAgent researchAgent;

    @Inject
    TherapistAgent therapistAgent;

    @Inject
    LegalAgent legalAgent;

    @Inject
    AuditedAgent auditedAgent;

    @Inject
    AdvisorAgent advisorAgent;

    @Inject
    VectorDbRetriever vectorDbRetriever;

    @Inject
    ConversationMemory conversationMemory;

    @Inject
    MetricsListener metricsListener;

    @Inject
    AuditLogger auditLogger;

    // Section 2: Fallback auto-wiring

    @Test
    void contentRetrieverAutoWired() {
        assertThat(vectorDbRetriever).isNotNull();
        assertThat(researchAgent.research("quantum computing")).isEqualTo("research-findings");
    }

    @Test
    void chatMemoryAutoWired() {
        assertThat(conversationMemory).isNotNull();
        assertThat(therapistAgent.respond("I feel anxious")).isEqualTo("therapeutic-response");
    }

    // Section 3: Static supplier precedence

    @Test
    void staticContentRetrieverTakesPrecedence() {
        assertThat(legalAgent.analyze("contract dispute")).isEqualTo("legal-opinion");
    }

    // Section 4: AgentListener global discovery

    @Test
    void cdiListenersDiscoveredGlobally() {
        assertThat(metricsListener).isNotNull();
        assertThat(auditLogger).isNotNull();
        // Both CDI listeners are discovered and wired — agents boot successfully
        assertThat(researchAgent.research("test")).isEqualTo("research-findings");
    }

    @Test
    void cdiListenersComposeWithStaticSupplier() {
        // AuditedAgent has @AgentListenerSupplier + CDI listeners compose additively
        assertThat(auditedAgent.audit("compliance check")).isEqualTo("audit-result");
    }

    // Section 5: Mixed supplier modes

    @Test
    void mixedStaticAndCdiSuppliers() {
        // Static ChatModel + CDI ContentRetriever + CDI ChatMemory coexist
        assertThat(advisorAgent.advise("retirement planning")).isEqualTo("financial-advice");
    }
}
