package io.quarkiverse.langchain4j.agentic.deployment.classform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.agentic.deployment.Agents;
import io.quarkiverse.langchain4j.agentic.deployment.decision.FixedDecisionModel;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * A class-based agent without a {@code @ChatModelSupplier} or {@code @DecisionModelSupplier} helper looks its model
 * bean up programmatically, on its first use. Nothing injects those beans here: the chat agent names its own
 * {@code chatLanguageModelSupplier} in {@code @RegisterAiService}, so the context of its inner AI service does not
 * inject the default chat model either. ArC would remove the beans as unused, and the agents would fail at their
 * first use, unless the extension keeps them.
 */
public class ClassBasedAgentDefaultModelBeansTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(FixedDecisionModel.class,
                    Agents.FixedResponseChatModel.class))
            .overrideConfigKey("quarkus.langchain4j.openai.decision-model.enabled", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true");

    public static class SupplierModel implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new Agents.FixedResponseChatModel("a poem from the supplier");
        }
    }

    /**
     * Relies on the default chat model bean, the OpenAI one, which nothing else injects: the model of an agent is
     * chosen by {@code @ModelName} or a {@code @ChatModelSupplier} helper, not by the {@code chatLanguageModelSupplier}
     * attribute, which only removes the injection of the default model from the context of the inner AI service.
     */
    @RegisterAiService(chatLanguageModelSupplier = SupplierModel.class)
    public static class Poet {

        @UserMessage("Write a poem about {{topic}}")
        @Agent(description = "Writes poems", outputKey = "poem")
        public native String write(@V("topic") String topic);
    }

    /** Relies on the default decision model bean, produced below, which nothing else injects. */
    public static class SpamDetector {

        @Agent(description = "Detects spam")
        @Decide("Is this message spam?")
        public native boolean isSpam(@V("message") String message);
    }

    @ApplicationScoped
    public static class Models {

        @Produces
        @ApplicationScoped
        DecisionModel decisionModel() {
            return new FixedDecisionModel("fixed").yesNo("isSpam", 0.9);
        }
    }

    @Inject
    Poet poet;

    @Inject
    SpamDetector spamDetector;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void theDefaultChatModelBeanIsKeptForAClassBasedAgent() {
        mock.chat().reply("a poem from the default model");
        assertThat(poet.write("roses")).isEqualTo("a poem from the default model");
    }

    @Test
    void theDefaultDecisionModelBeanIsKeptForAClassBasedDecisionAgent() {
        assertThat(spamDetector.isSpam("Buy now!")).isTrue();
    }
}
