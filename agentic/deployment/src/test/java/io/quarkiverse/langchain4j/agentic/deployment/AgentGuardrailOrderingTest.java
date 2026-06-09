package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

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

@SuppressWarnings("CdiInjectionPointsInspection")
public class AgentGuardrailOrderingTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(OrderedGuardedAgent.class,
                                    FirstGuardrail.class, SecondGuardrail.class, ExecutionLog.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    OrderedGuardedAgent agent;

    @Inject
    ExecutionLog log;

    @BeforeEach
    void clearLog() {
        log.clear();
    }

    @Test
    @ActivateRequestContext
    void guardrailsExecuteInDeclarationOrder() {
        agent.process("test");

        assertThat(log.entries()).containsExactly("First", "Second");
    }

    public interface OrderedGuardedAgent {

        @Agent
        @InputGuardrails({ FirstGuardrail.class, SecondGuardrail.class })
        @UserMessage("Process: {{request}}")
        String process(@V("request") String request);
    }

    @ApplicationScoped
    public static class ExecutionLog {
        private final List<String> entries = new ArrayList<>();

        public synchronized void add(String entry) {
            entries.add(entry);
        }

        public synchronized List<String> entries() {
            return new ArrayList<>(entries);
        }

        public synchronized void clear() {
            entries.clear();
        }
    }

    @ApplicationScoped
    public static class FirstGuardrail implements InputGuardrail {

        @Inject
        ExecutionLog log;

        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage userMessage) {
            log.add("First");
            return success();
        }
    }

    @ApplicationScoped
    public static class SecondGuardrail implements InputGuardrail {

        @Inject
        ExecutionLog log;

        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage userMessage) {
            log.add("Second");
            return success();
        }
    }
}
