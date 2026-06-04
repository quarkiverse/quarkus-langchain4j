package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies S-3: @CdiBean parameters on supplier methods declared on parent interfaces
 * are marked as unremovable, preventing UnsatisfiedResolutionException at runtime.
 */
public class S3CdiBeanInheritedSupplierTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ModelSelector.class, BaseAgent.class, ConcreteAgent.class,
                            Agents.FixedResponseChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    /** CDI bean that selects a model — declared as @CdiBean parameter on a parent interface supplier. */
    @Singleton
    public static class ModelSelector {
        public ChatModel select() {
            return new Agents.FixedResponseChatModel("selected");
        }
    }

    /** Base interface declares the @ChatModelSupplier with a @CdiBean parameter. */
    public interface BaseAgent {
        @ChatModelSupplier
        static ChatModel model(@CdiBean ModelSelector selector) {
            return selector.select();
        }
    }

    /** Concrete agent extends base — the @ChatModelSupplier is on the parent interface. */
    public interface ConcreteAgent extends BaseAgent {
        @UserMessage("{{input}}")
        @Agent(description = "Agent using inherited CDI supplier")
        String answer(String input);
    }

    @Inject
    ConcreteAgent agent;

    @Test
    void agentBootsWithoutUnsatisfiedResolution() {
        // If ModelSelector were removable, Arc would fail at boot before reaching this.
        assertThat(agent).isNotNull();
    }
}
