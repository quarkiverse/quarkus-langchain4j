package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Produces;
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
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class CdiSupplierParameterResolverTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(
                                    ModelSelector.class,
                                    EchoAgent.class, ModelSelectingAgent.class, SequenceWrapper.class,
                                    MixedParamsAgent.class, MixedParamsSequenceWrapper.class,
                                    Agents.FixedResponseChatModel.class, Agents.EchoResponseChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.echo.api-key", "echo")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.fixed.api-key", "fixed")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    static final String SELECTED_RESPONSE = "selected-by-cdi-bean";

    @Singleton
    public static class ModelSelector {

        @ModelName("fixed")
        ChatModel fixed;

        @ModelName("echo")
        ChatModel echo;

        public ChatModel select(String input) {
            if (input.contains("a")) {
                return fixed;
            }
            return echo;
        }
    }

    @Singleton
    public static class ModelProducer {

        @Produces
        @ModelName("fixed")
        public ChatModel fixed() {
            return new Agents.FixedResponseChatModel(SELECTED_RESPONSE);
        }

        @Produces
        @ModelName("echo")
        public ChatModel echo() {
            return new Agents.EchoResponseChatModel();
        }
    }

    public interface EchoAgent {

        @UserMessage("{{text}}")
        @Agent(description = "An agent echoing its input", outputKey = "echo")
        String echo(String text);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.EchoResponseChatModel();
        }
    }

    public interface ModelSelectingAgent {

        @UserMessage("Answer: {{text}}")
        @Agent(description = "An agent using a CDI-resolved model selector", outputKey = "answer")
        String answer(String text);

        @ChatModelSupplier
        static ChatModel chatModel(@CdiBean ModelSelector selector, String echo) {
            return selector.select(echo);
        }
    }

    public interface SequenceWrapper {

        @SequenceAgent(outputKey = "answer", subAgents = { EchoAgent.class, ModelSelectingAgent.class })
        String run(String text);
    }

    public interface MixedParamsAgent {

        @UserMessage("Answer: {{text}}")
        @Agent(description = "An agent mixing scope and CDI params", outputKey = "answer")
        String answer(String text);

        @ChatModelSupplier
        static ChatModel chatModel(@V("echo") String fromScope, @CdiBean ModelSelector fromCdi) {
            return fromCdi.select(fromScope);
        }
    }

    public interface MixedParamsSequenceWrapper {

        @SequenceAgent(outputKey = "answer", subAgents = { EchoAgent.class, MixedParamsAgent.class })
        String run(String text);
    }

    @Inject
    SequenceWrapper sequenceWrapper;

    @Inject
    MixedParamsSequenceWrapper mixedParamsSequenceWrapper;

    @Test
    void cdiResolvedBeanIsUsedInChatModelSupplier() {
        String result = sequenceWrapper.run("cat");
        assertThat(result).isEqualTo(SELECTED_RESPONSE);
    }

    @Test
    void cdiResolvedBeanIsUsedWhenInputDoesNotMatch() {
        String result = sequenceWrapper.run("dog");
        assertThat(result).startsWith("Answer: dog");
    }

    @Test
    void mixedScopeAndCdiParamsWork() {
        String result = mixedParamsSequenceWrapper.run("cat");
        assertThat(result).isEqualTo(SELECTED_RESPONSE);
    }
}
