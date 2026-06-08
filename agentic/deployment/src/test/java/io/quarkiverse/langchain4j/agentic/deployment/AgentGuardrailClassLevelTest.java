package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
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
 * Verifies method-level @InputGuardrails takes precedence over class-level.
 * The agent has ClassLevelGuardrail on the interface and MethodLevelGuardrail on the method.
 * Only MethodLevelGuardrail should fire.
 */
@SuppressWarnings("CdiInjectionPointsInspection")
public class AgentGuardrailClassLevelTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(PrecedenceAgent.class,
                                    ClassLevelGuardrail.class, MethodLevelGuardrail.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    PrecedenceAgent agent;

    @Inject
    ClassLevelGuardrail classGuardrail;

    @Inject
    MethodLevelGuardrail methodGuardrail;

    @Test
    @ActivateRequestContext
    void methodLevelGuardrailTakesPrecedenceOverClassLevel() {
        agent.process("test");

        assertThat(methodGuardrail.invocationCount()).isEqualTo(1);
        assertThat(classGuardrail.invocationCount()).isEqualTo(0);
    }

    @InputGuardrails(ClassLevelGuardrail.class)
    public interface PrecedenceAgent {

        @Agent
        @InputGuardrails(MethodLevelGuardrail.class)
        @UserMessage("Process: {{request}}")
        String process(@V("request") String request);
    }

    @ApplicationScoped
    public static class ClassLevelGuardrail implements InputGuardrail {
        private final AtomicInteger count = new AtomicInteger(0);

        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage userMessage) {
            count.incrementAndGet();
            return success();
        }

        public int invocationCount() {
            return count.get();
        }
    }

    @ApplicationScoped
    public static class MethodLevelGuardrail implements InputGuardrail {
        private final AtomicInteger count = new AtomicInteger(0);

        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage userMessage) {
            count.incrementAndGet();
            return success();
        }

        public int invocationCount() {
            return count.get();
        }
    }
}
