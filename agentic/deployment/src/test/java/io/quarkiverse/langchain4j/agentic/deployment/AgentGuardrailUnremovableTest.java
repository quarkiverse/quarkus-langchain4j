package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.InputGuardrails;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that a guardrail bean referenced ONLY from an agent interface
 * (not injected anywhere else) survives Arc's dead-code elimination.
 */
@SuppressWarnings("CdiInjectionPointsInspection")
public class AgentGuardrailUnremovableTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(AgentWithGuardrail.class, OnlyAgentReferencedGuardrail.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    AgentWithGuardrail agent;

    @BeforeEach
    void resetStaticState() {
        OnlyAgentReferencedGuardrail.invoked.set(false);
    }

    @Test
    @ActivateRequestContext
    void guardrailBeanSurvivesArcDeadCodeElimination() {
        assertThat(OnlyAgentReferencedGuardrail.invoked.get()).isFalse();
        agent.process("test");
        assertThat(OnlyAgentReferencedGuardrail.invoked.get()).isTrue();
    }

    public interface AgentWithGuardrail {

        @Agent
        @InputGuardrails(OnlyAgentReferencedGuardrail.class)
        @UserMessage("Process: {{request}}")
        String process(@V("request") String request);
    }

    @ApplicationScoped
    public static class OnlyAgentReferencedGuardrail implements InputGuardrail {

        static final AtomicBoolean invoked = new AtomicBoolean(false);

        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage userMessage) {
            invoked.set(true);
            return success();
        }
    }
}
