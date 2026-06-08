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
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tier 2: @Agent with @CdiBean on @ChatModelSupplier parameters.
 * <p>
 * The supplier method is the declaration site (portable), and @CdiBean
 * tells Quarkus to resolve the parameter from CDI at build time.
 * <p>
 * Currently @CdiBean parameters are supported on @ChatModelSupplier only —
 * upstream issue #5377 tracks generalising the parameter resolver SPI to
 * all supplier types.
 * <p>
 * See {@link CdiChatSupplierParameterResolverTest} for the full gallery
 * of @CdiBean scenarios (mixed params, qualifiers, scope resolution).
 */
public class CdiWiringTier2CdiBeanTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ModelSelector.class, EchoAgent.class,
                            CdiModelAgent.class, Wrapper.class,
                            Agents.FixedResponseChatModel.class,
                            Agents.EchoResponseChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Singleton
    public static class ModelSelector {
        public ChatModel select() {
            return new Agents.FixedResponseChatModel("cdi-selected");
        }
    }

    public interface EchoAgent {
        @UserMessage("{{text}}")
        @Agent(description = "Echo agent", outputKey = "echo")
        String echo(String text);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.EchoResponseChatModel();
        }
    }

    public interface CdiModelAgent {
        @UserMessage("Answer: {{text}}")
        @Agent(description = "Agent with CDI-selected model", outputKey = "answer")
        String answer(String text);

        @ChatModelSupplier
        static ChatModel chatModel(@CdiBean ModelSelector selector) {
            return selector.select();
        }
    }

    public interface Wrapper {
        @SequenceAgent(outputKey = "answer", subAgents = { EchoAgent.class, CdiModelAgent.class })
        String run(String text);
    }

    @Inject
    Wrapper wrapper;

    @Test
    void cdiBeanParameterResolvesModelFromCdi() {
        String result = wrapper.run("test-input");
        assertThat(result).isEqualTo("cdi-selected");
    }
}
