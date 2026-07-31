package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.deployment.Agents.CategoryRouter;
import io.quarkiverse.langchain4j.agentic.deployment.Agents.FixedResponseChatModel;
import io.quarkiverse.langchain4j.agentic.deployment.Agents.RequestCategory;
import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatScopeConversationBridge;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.runtime.conversation.ConversationContext;
import io.quarkiverse.langchain4j.runtime.conversation.ConversationEnded;
import io.quarkiverse.langchain4j.runtime.conversation.ConversationStarted;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;

public class ChatScopedAgenticConversationTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(FixedResponseChatModel.class,
                            CategoryRouter.class,
                            RequestCategory.class,
                            MedicalExpertChatScoped.class,
                            TechnicalExpertChatScoped.class,
                            LegalExpertChatScoped.class,
                            ChatScopedExpertsAgent.class,
                            ChatScopedExpertRouter.class,
                            ChatScopeConversationBridge.class,
                            ConversationEventCollector.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    public interface MedicalExpertChatScoped {

        @UserMessage("""
                You are a medical expert.
                Analyze the following user request under a medical point of view and provide the best possible answer.
                The user request is {{request}}.
                """)
        @Agent(description = "A medical expert", outputKey = "response")
        @ChatScoped
        String medical(@V("request") String request);

        @ChatMemoryProviderSupplier
        static ChatMemory chatMemory(Object memoryId) {
            return MessageWindowChatMemory.withMaxMessages(10);
        }

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new FixedResponseChatModel("\"I'm sorry to hear that you've broken your leg." +
                    "Here are the steps you should take: **Seek Medical Attention");
        }
    }

    public interface LegalExpertChatScoped {

        @UserMessage("""
                You are a legal expert.
                Analyze the following user request under a legal point of view and provide the best possible answer.
                The user request is {{request}}.
                """)
        @Agent(description = "A legal expert", outputKey = "response")
        @ChatScoped
        String legal(@V("request") String request);

        @ChatMemoryProviderSupplier
        static ChatMemory chatMemory(Object memoryId) {
            return MessageWindowChatMemory.withMaxMessages(10);
        }

        @ChatModelSupplier
        static ChatModel chatModel(AgenticScope scope) {
            String response = scope.readState("response", "");
            if (response.contains("leg")) {
                return new FixedResponseChatModel("\"I'm sorry to hear that you've broken your leg." +
                        "Here are the steps you should take: **Seek Legal Attention");
            }
            if (response.contains("computer")) {
                return new FixedResponseChatModel("\"I'm sorry to hear that you've broken your computer." +
                        "Here are the steps you should take: **Seek Legal Attention");
            }
            return new FixedResponseChatModel("**Seek Legal Attention");
        }
    }

    public interface TechnicalExpertChatScoped {

        @UserMessage("""
                You are a technical expert.
                Analyze the following user request under a technical point of view and provide the best possible answer.
                The user request is {{request}}.
                """)
        @Agent(description = "A technical expert", outputKey = "response")
        @ChatScoped
        String technical(@V("request") String request);

        @ChatMemoryProviderSupplier
        static ChatMemory chatMemory(Object memoryId) {
            return MessageWindowChatMemory.withMaxMessages(10);
        }

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new FixedResponseChatModel("\"I'm sorry to hear that you've broken your computer." +
                    "Here are the steps you should take: **Seek Technical Assistance");
        }
    }

    public interface ChatScopedExpertsAgent {

        @ConditionalAgent(outputKey = "response", subAgents = { MedicalExpertChatScoped.class,
                TechnicalExpertChatScoped.class, LegalExpertChatScoped.class })
        String askExpert(@V("request") String request);

        @ActivationCondition(MedicalExpertChatScoped.class)
        static boolean activateMedical(@V("category") RequestCategory category) {
            return category == RequestCategory.MEDICAL;
        }

        @ActivationCondition(TechnicalExpertChatScoped.class)
        static boolean activateTechnical(@V("category") RequestCategory category) {
            return category == RequestCategory.TECHNICAL;
        }

        @ActivationCondition(LegalExpertChatScoped.class)
        static boolean activateLegal(@V("category") RequestCategory category) {
            return category == RequestCategory.LEGAL;
        }
    }

    public interface ChatScopedExpertRouter extends AgenticScopeAccess {

        @SequenceAgent(outputKey = "response", subAgents = { CategoryRouter.class, ChatScopedExpertsAgent.class })
        @ChatScoped
        String ask(@MemoryId String memoryId, @V("request") String request);
    }

    @Inject
    ChatScopedExpertRouter expertRouter;

    @Inject
    ConversationEventCollector collector;

    @BeforeEach
    void reset() {
        collector.clear();
    }

    @Test
    @RunOnVertxContext(runOnEventLoop = false)
    void multiInteractionConversationsWithChatScopedAgent(UniAsserter asserter) {
        asserter.execute(() -> {
            // First conversation — medical then legal follow-up (2 interactions)
            ChatScope.begin();
            String conv1 = ChatScope.id();

            expertRouter.ask(conv1, "I broke my leg, what should I do?");
            assertThat(expertRouter.getAgenticScope(conv1)
                    .readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.MEDICAL);
            assertThat(ConversationContext.current()).isEqualTo(conv1);

            String legalResponse1 = expertRouter.ask(conv1, "Should I sue my neighbor who caused this damage?");
            assertThat(expertRouter.getAgenticScope(conv1)
                    .readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.LEGAL);
            assertThat(legalResponse1).containsIgnoringCase("leg").doesNotContain("computer");
            assertThat(ConversationContext.current()).isEqualTo(conv1);

            ChatScope.end();
            assertThat(ConversationContext.isActive()).isFalse();

            // Second conversation — technical then legal follow-up (2 interactions)
            ChatScope.begin();
            String conv2 = ChatScope.id();
            assertThat(conv2).isNotEqualTo(conv1);

            expertRouter.ask(conv2, "My computer has liquid inside, what should I do?");
            assertThat(expertRouter.getAgenticScope(conv2)
                    .readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.TECHNICAL);
            assertThat(ConversationContext.current()).isEqualTo(conv2);

            String legalResponse2 = expertRouter.ask(conv2, "Should I sue my neighbor who caused this damage?");
            assertThat(expertRouter.getAgenticScope(conv2)
                    .readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.LEGAL);
            assertThat(legalResponse2).containsIgnoringCase("computer").doesNotContain("leg");
            assertThat(ConversationContext.current()).isEqualTo(conv2);

            ChatScope.end();
            assertThat(ConversationContext.isActive()).isFalse();

            // Both conversations tracked — each had 2 interactions but only 1 begin/end per scope
            assertThat(collector.started()).containsExactly(conv1, conv2);
            assertThat(collector.ended()).containsExactly(conv1, conv2);
        });
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
