package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

@SuppressWarnings("CdiInjectionPointsInspection")
public class AgentOutputGuardrailTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(OutputGuardedAgent.class, SpyOutputGuardrail.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    OutputGuardedAgent agent;

    @Inject
    SpyOutputGuardrail guardrail;

    @Test
    @ActivateRequestContext
    void outputGuardrailFiresAfterLlmCall() {
        assertThat(guardrail.invocationCount()).isEqualTo(0);
        assertThat(guardrail.lastMessage()).isNull();

        agent.process("hello");

        assertThat(guardrail.invocationCount()).isEqualTo(1);
        assertThat(guardrail.lastMessage()).isNotNull();
    }

    public interface OutputGuardedAgent {

        @Agent
        @OutputGuardrails(SpyOutputGuardrail.class)
        @UserMessage("Process: {{request}}")
        String process(@V("request") String request);
    }

    @ApplicationScoped
    public static class SpyOutputGuardrail implements OutputGuardrail {

        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicReference<AiMessage> lastMessage = new AtomicReference<>();

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            count.incrementAndGet();
            lastMessage.set(responseFromLLM);
            return success();
        }

        public int invocationCount() {
            return count.get();
        }

        public AiMessage lastMessage() {
            return lastMessage.get();
        }
    }
}
