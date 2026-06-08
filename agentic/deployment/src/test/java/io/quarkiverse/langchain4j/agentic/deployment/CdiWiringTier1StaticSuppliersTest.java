package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatMemorySupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ContentRetrieverSupplier;
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
 * Tier 1: @Agent with static suppliers only — no CDI wiring.
 * <p>
 * All dependencies are declared via static supplier methods on the agent interface.
 * This is the most portable mode — the interface works identically in plain
 * langchain4j, langchain4j-cdi, and Quarkus.
 */
public class CdiWiringTier1StaticSuppliersTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ResearchAgent.class, InlineRetriever.class, InlineMemory.class,
                            Agents.FixedResponseChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    public static class InlineRetriever implements ContentRetriever {
        @Override
        public List<Content> retrieve(Query query) {
            return List.of(Content.from(TextSegment.from("static-retriever-result")));
        }
    }

    public static class InlineMemory implements ChatMemory {
        private final List<ChatMessage> messages = new ArrayList<>();

        @Override
        public Object id() {
            return "static";
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

    public interface ResearchAgent {

        @UserMessage("Research: {{topic}}")
        @Agent(description = "Static-only agent", outputKey = "findings")
        String research(@V("topic") String topic);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("static-findings");
        }

        @ContentRetrieverSupplier
        static ContentRetriever retriever() {
            return new InlineRetriever();
        }

        @ChatMemorySupplier
        static ChatMemory memory() {
            return new InlineMemory();
        }
    }

    @Inject
    ResearchAgent agent;

    @Test
    void agentBootsAndRespondsWithStaticSuppliersOnly() {
        assertThat(agent).isNotNull();
        assertThat(agent.research("quantum computing")).isEqualTo("static-findings");
    }
}
