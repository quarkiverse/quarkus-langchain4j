package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.runtime.observability.AgentCompletedEvent;
import io.quarkiverse.langchain4j.agentic.runtime.observability.AgentErrorEvent;
import io.quarkiverse.langchain4j.agentic.runtime.observability.AgentStartedEvent;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class AgentCdiEventListenerTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestAgent.class, EventCapture.class, Agents.FixedResponseChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    public interface TestAgent {
        @UserMessage("Answer: {{request}}")
        @Agent(description = "test agent")
        String ask(@V("request") String request);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("test-response");
        }
    }

    @ApplicationScoped
    public static class EventCapture {
        private final List<AgentStartedEvent> started = new CopyOnWriteArrayList<>();
        private final List<AgentCompletedEvent> completed = new CopyOnWriteArrayList<>();
        private final List<AgentErrorEvent> errors = new CopyOnWriteArrayList<>();

        public void onStarted(@Observes AgentStartedEvent event) {
            started.add(event);
        }

        public void onCompleted(@Observes AgentCompletedEvent event) {
            completed.add(event);
        }

        public void onError(@Observes AgentErrorEvent event) {
            errors.add(event);
        }

        public List<AgentStartedEvent> getStarted() {
            return started;
        }

        public List<AgentCompletedEvent> getCompleted() {
            return completed;
        }

        public List<AgentErrorEvent> getErrors() {
            return errors;
        }

        public void reset() {
            started.clear();
            completed.clear();
            errors.clear();
        }
    }

    @Inject
    TestAgent agent;

    @Inject
    EventCapture capture;

    @BeforeEach
    void reset() {
        capture.reset();
    }

    @Test
    void agentInvocationFiresStartedAndCompletedEvents() {
        agent.ask("hello");

        assertThat(capture.getStarted()).hasSize(1);
        assertThat(capture.getStarted().get(0).agentName()).isNotBlank();

        assertThat(capture.getCompleted()).hasSize(1);
        assertThat(capture.getCompleted().get(0).agentName()).isEqualTo(capture.getStarted().get(0).agentName());
        assertThat(capture.getCompleted().get(0).output()).isNotNull();
        assertThat(capture.getCompleted().get(0).durationNanos()).isGreaterThan(0);

        assertThat(capture.getErrors()).isEmpty();
    }
}
