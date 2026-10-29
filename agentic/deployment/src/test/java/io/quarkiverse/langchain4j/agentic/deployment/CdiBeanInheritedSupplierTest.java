package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies that @CdiBean parameters on supplier and workflow helper methods declared on parent interfaces
 * are marked as unremovable, preventing UnsatisfiedResolutionException at runtime.
 */
public class CdiBeanInheritedSupplierTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ModelSelector.class, BaseAgent.class, ConcreteAgent.class,
                            Agents.FixedResponseChatModel.class,
                            Threshold.class, Counter.class, BaseLoop.class, ConcreteLoop.class))
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

    /** CDI bean only used as a @CdiBean parameter of an exit condition declared on a parent interface. */
    @ApplicationScoped
    public static class Threshold {
        public int value() {
            return 3;
        }
    }

    public static class Counter {
        @Agent(outputKey = "count")
        public static int increment(@V("count") int count) {
            return count + 1;
        }
    }

    /** Base interface declares the @ExitCondition with a @CdiBean parameter. */
    public interface BaseLoop {
        @ExitCondition
        static boolean reached(@V("count") int count, @CdiBean Threshold threshold) {
            return count >= threshold.value();
        }
    }

    /** Concrete loop extends base — the @ExitCondition is on the parent interface. */
    public interface ConcreteLoop extends BaseLoop {
        @LoopAgent(outputKey = "count", maxIterations = 10, subAgents = Counter.class)
        int count(@V("count") int count);
    }

    @Inject
    ConcreteLoop loop;

    @Test
    void inheritedExitConditionResolvesItsCdiBean() {
        assertThat(loop.count(0)).isEqualTo(3);
    }
}
