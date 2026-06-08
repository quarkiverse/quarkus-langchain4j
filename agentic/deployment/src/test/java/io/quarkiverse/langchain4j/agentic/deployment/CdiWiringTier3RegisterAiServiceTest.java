package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;

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
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tier 3: @Agent with @RegisterAiService — opt-in CDI auto-wiring.
 * <p>
 * Adding @RegisterAiService to an agent interface opts into the Quarkus
 * CDI auto-wiring model. Properties on @RegisterAiService explicitly
 * control which suppliers are wired, with opt-out via NoXxxSupplier classes.
 * <p>
 * This test verifies two agents in the same deployment:
 * <ul>
 * <li>ResearchAgent uses @RegisterAiService — gets the RetrievalAugmentor bean</li>
 * <li>PlainAgent has no @RegisterAiService — does NOT get the augmentor (no cross-contamination)</li>
 * </ul>
 */
public class CdiWiringTier3RegisterAiServiceTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAugmentorSupplier.class, ResearchAgent.class, PlainAgent.class,
                            Agents.FixedResponseChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @ApplicationScoped
    public static class MyAugmentorSupplier implements Supplier<RetrievalAugmentor> {
        @Override
        public RetrievalAugmentor get() {
            return request -> new AugmentationResult(request.chatMessage(),
                    List.of(Content.from(TextSegment.from("augmented-context"))));
        }
    }

    @RegisterAiService(retrievalAugmentor = MyAugmentorSupplier.class)
    public interface ResearchAgent {

        @UserMessage("Research: {{topic}}")
        @Agent(description = "Agent with @RegisterAiService opt-in", outputKey = "findings")
        String research(@V("topic") String topic);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("registered-findings");
        }
    }

    public interface PlainAgent {

        @UserMessage("{{input}}")
        @Agent(description = "Agent without @RegisterAiService")
        String process(String input);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("plain-response");
        }
    }

    @Inject
    ResearchAgent researchAgent;

    @Inject
    PlainAgent plainAgent;

    @Test
    void registeredAgentGetsAugmentor() {
        assertThat(researchAgent).isNotNull();
        assertThat(researchAgent.research("quantum computing")).isEqualTo("registered-findings");
    }

    @Test
    void plainAgentDoesNotGetAugmentor() {
        assertThat(plainAgent).isNotNull();
        assertThat(plainAgent.process("hello")).isEqualTo("plain-response");
    }
}
