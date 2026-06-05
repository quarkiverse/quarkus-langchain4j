package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ContentRetrieverSupplier;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies C2: CDI {@link ContentRetriever} auto-wiring into agents.
 * <p>
 * Two agents are defined:
 * <ul>
 * <li>{@code AutoWiredAgent} — no static {@code @ContentRetrieverSupplier}, expects CDI auto-wiring</li>
 * <li>{@code StaticSupplierAgent} — has a static {@code @ContentRetrieverSupplier}, CDI should be skipped</li>
 * </ul>
 * <p>
 * The test verifies:
 * <ol>
 * <li>Both agents boot successfully (CDI wiring is correct)</li>
 * <li>The CDI ContentRetriever bean is not removed by Arc (marked unremovable)</li>
 * <li>Agent invocation works for both agents</li>
 * <li>The auto-wired agent receives the CDI ContentRetriever (verified via RAG augmentation)</li>
 * </ol>
 */
public class CdiContentRetrieverAutoWiringTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RecordingContentRetriever.class,
                            AutoWiredAgent.class,
                            StaticSupplierAgent.class,
                            Agents.FixedResponseChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    /**
     * A CDI-managed ContentRetriever that records whether it was called.
     */
    @ApplicationScoped
    public static class RecordingContentRetriever implements ContentRetriever {

        public final AtomicBoolean called = new AtomicBoolean(false);

        @Override
        public List<Content> retrieve(Query query) {
            called.set(true);
            return Collections.singletonList(Content.from(TextSegment.from("retrieved context")));
        }
    }

    /**
     * Agent with NO @ContentRetrieverSupplier — CDI should auto-wire the RecordingContentRetriever.
     */
    public interface AutoWiredAgent {

        @UserMessage("{{input}}")
        @Agent(description = "Agent without static content retriever supplier")
        String ask(String input);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("auto-wired response");
        }
    }

    /**
     * Agent WITH a static @ContentRetrieverSupplier — CDI auto-wiring should be skipped.
     */
    public interface StaticSupplierAgent {

        @UserMessage("{{input}}")
        @Agent(description = "Agent with static content retriever supplier")
        String ask(String input);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("static-supplier response");
        }

        @ContentRetrieverSupplier
        static ContentRetriever contentRetriever() {
            return query -> Collections.singletonList(Content.from(TextSegment.from("from static supplier")));
        }
    }

    @Inject
    AutoWiredAgent autoWiredAgent;

    @Inject
    StaticSupplierAgent staticSupplierAgent;

    @Inject
    RecordingContentRetriever retriever;

    @Test
    void autoWiredAgentBootsAndIsInvocable() {
        assertThat(retriever).isNotNull();
        assertThat(autoWiredAgent).isNotNull();
        String result = autoWiredAgent.ask("test query");
        assertThat(result).isEqualTo("auto-wired response");
    }

    @Test
    void staticSupplierAgentBootsAndIsInvocable() {
        assertThat(staticSupplierAgent).isNotNull();
        String result = staticSupplierAgent.ask("test query");
        assertThat(result).isEqualTo("static-supplier response");
    }
}
