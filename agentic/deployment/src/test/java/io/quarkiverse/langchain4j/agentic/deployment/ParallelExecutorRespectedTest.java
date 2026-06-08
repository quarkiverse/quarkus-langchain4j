package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.ParallelExecutor;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class ParallelExecutorRespectedTest extends OpenAiBaseTest {

    static final AtomicReference<String> capturedThreadName = new AtomicReference<>();

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ThreadCapturingAgent.class, SimpleAgent.class,
                            CustomExecutorParallelAgent.class, ThreadCapturingChatModel.class,
                            Agents.FixedResponseChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    /**
     * A ChatModel that captures the thread name during doChat — this runs on the
     * parallel executor's worker thread, letting us verify which executor was used.
     */
    public static class ThreadCapturingChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            capturedThreadName.set(Thread.currentThread().getName());
            return ChatResponse.builder().aiMessage(new AiMessage("captured")).build();
        }
    }

    public interface ThreadCapturingAgent {
        @UserMessage("{{input}}")
        @Agent(description = "Captures thread name", outputKey = "captured")
        String capture(@V("input") String input);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new ThreadCapturingChatModel();
        }
    }

    public interface SimpleAgent {
        @UserMessage("{{input}}")
        @Agent(description = "Simple echo", outputKey = "echo")
        String echo(@V("input") String input);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("echoed");
        }
    }

    public record Result(String captured, String echo) {
    }

    public interface CustomExecutorParallelAgent {
        @ParallelAgent(outputKey = "result", subAgents = { ThreadCapturingAgent.class, SimpleAgent.class })
        List<Result> run(@V("input") String input);

        @ParallelExecutor
        static Executor executor() {
            return Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r);
                t.setName("custom-parallel-" + t.getId());
                return t;
            });
        }
    }

    @Inject
    CustomExecutorParallelAgent agent;

    @Test
    void userDeclaredParallelExecutorIsUsed() {
        agent.run("test");
        assertThat(capturedThreadName.get())
                .as("Sub-agent should run on custom executor thread, not ManagedExecutor")
                .startsWith("custom-parallel-");
    }
}
