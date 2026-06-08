package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class AgentMetricsListenerTest extends OpenAiBaseTest {

    @BeforeAll
    static void addSimpleRegistry() {
        Metrics.globalRegistry.add(new SimpleMeterRegistry());
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MetricsTestAgent.class, Agents.FixedResponseChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    public interface MetricsTestAgent {
        @UserMessage("Answer: {{request}}")
        @Agent(description = "metrics test agent")
        String ask(@V("request") String request);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("metrics-response");
        }
    }

    @Inject
    MetricsTestAgent agent;

    @Test
    void agentInvocationRecordsInvocationCounterAndDurationTimer() {
        agent.ask("test");

        Collection<Counter> counters = Metrics.globalRegistry
                .find("gen_ai.agent.invocations").counters();
        assertThat(counters).isNotEmpty();
        assertThat(counters.stream().mapToDouble(Counter::count).sum()).isGreaterThan(0);

        Collection<Timer> timers = Metrics.globalRegistry
                .find("gen_ai.agent.duration").timers();
        assertThat(timers).isNotEmpty();
        assertThat(timers.stream().mapToLong(Timer::count).sum()).isGreaterThan(0);
    }

    @Test
    void successfulInvocationTaggedWithNoError() {
        agent.ask("test");

        Counter counter = Metrics.globalRegistry.find("gen_ai.agent.invocations")
                .tag("error.type", "none")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isGreaterThan(0);
    }
}
