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

@SuppressWarnings("CdiInjectionPointsInspection")
public class AgentInputGuardrailTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(GuardedAgent.class, SpyInputGuardrail.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    GuardedAgent agent;

    @Inject
    SpyInputGuardrail guardrail;

    @Test
    @ActivateRequestContext
    void inputGuardrailFiresOnAgentInvocation() {
        assertThat(guardrail.invocationCount()).isEqualTo(0);
        agent.process("hello");
        assertThat(guardrail.invocationCount()).isEqualTo(1);
    }

    public interface GuardedAgent {

        @Agent
        @InputGuardrails(SpyInputGuardrail.class)
        @UserMessage("Process: {{request}}")
        String process(@V("request") String request);
    }

    @ApplicationScoped
    public static class SpyInputGuardrail implements InputGuardrail {

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
