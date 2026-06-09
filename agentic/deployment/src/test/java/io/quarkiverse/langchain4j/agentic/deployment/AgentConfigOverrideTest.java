package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.V;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that {@code quarkus.langchain4j.agent."counting-loop".max-iterations}
 * overrides the annotation-declared {@code maxIterations=10} down to 3.
 */
public class AgentConfigOverrideTest {

    static final AtomicInteger iterationCount = new AtomicInteger(0);

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(CountingLoopAgent.class, IncrementAgent.class,
                            CountingModel.class, AgentConfigOverrideTest.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.agent.\"counting-loop\".max-iterations", "3")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url", "http://localhost");

    @Inject
    CountingLoopAgent agent;

    @Test
    void configOverridesAnnotationMaxIterations() {
        iterationCount.set(0);
        ResultWithAgenticScope<String> result = agent.loop("start");
        // Annotation says maxIterations=10, config overrides to 3.
        // Each loop iteration invokes IncrementAgent once, incrementing the counter.
        assertThat(iterationCount.get()).isEqualTo(3);
    }

    public interface IncrementAgent {

        @Agent(description = "Increments counter", outputKey = "result")
        String increment(@V("input") String input);

        @ChatModelSupplier
        static ChatModel model() {
            return new CountingModel();
        }
    }

    public interface CountingLoopAgent {

        @LoopAgent(name = "counting-loop", description = "Counts iterations", outputKey = "result", maxIterations = 10, subAgents = {
                IncrementAgent.class })
        ResultWithAgenticScope<String> loop(@V("input") String input);

        @ExitCondition
        static boolean shouldExit(@V("result") String result) {
            // Never exit voluntarily — rely solely on maxIterations
            return false;
        }

        @ChatModelSupplier
        static ChatModel model() {
            return new CountingModel();
        }
    }

    public static class CountingModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            iterationCount.incrementAndGet();
            return ChatResponse.builder().aiMessage(new AiMessage("iteration-" + iterationCount.get())).build();
        }
    }
}
