package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
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
 * Verifies C2: CDI auto-wiring edge cases.
 * <p>
 * This test verifies that when multiple CDI beans of the same supplier type exist,
 * auto-wiring is skipped (no error, agent boots normally).
 * <p>
 * Defines:
 * <ul>
 * <li>Two {@code @ApplicationScoped ContentRetriever} beans (FirstContentRetriever, SecondContentRetriever)</li>
 * <li>An agent WITHOUT {@code @ContentRetrieverSupplier} (would be ambiguous if auto-wired)</li>
 * </ul>
 * <p>
 * The test verifies:
 * <ol>
 * <li>Agent boots successfully despite multiple CDI beans (auto-wiring was skipped)</li>
 * <li>Agent is invocable (no runtime error)</li>
 * </ol>
 */
public class CdiAutoWiringEdgeCasesTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(FirstContentRetriever.class,
                            SecondContentRetriever.class,
                            MultipleBeansAgent.class,
                            Agents.FixedResponseChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    /**
     * First CDI-managed ContentRetriever bean.
     */
    @ApplicationScoped
    public static class FirstContentRetriever implements ContentRetriever {

        @Override
        public List<Content> retrieve(Query query) {
            return Collections.singletonList(Content.from(TextSegment.from("first retriever")));
        }
    }

    /**
     * Second CDI-managed ContentRetriever bean (causes ambiguity).
     */
    @ApplicationScoped
    public static class SecondContentRetriever implements ContentRetriever {

        @Override
        public List<Content> retrieve(Query query) {
            return Collections.singletonList(Content.from(TextSegment.from("second retriever")));
        }
    }

    /**
     * Agent with NO @ContentRetrieverSupplier — auto-wiring should be skipped due to multiple beans.
     */
    public interface MultipleBeansAgent {

        @UserMessage("{{input}}")
        @Agent(description = "Agent with no supplier and multiple CDI beans")
        String ask(String input);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("multiple-beans response");
        }
    }

    @Inject
    MultipleBeansAgent multipleBeansAgent;

    @Test
    void multipleBeansSkipAutoWiring() {
        // Agent boots despite two ContentRetriever beans (auto-wiring was skipped, no error)
        assertThat(multipleBeansAgent).isNotNull();
        String result = multipleBeansAgent.ask("test query");
        assertThat(result).isEqualTo("multiple-beans response");
    }
}
