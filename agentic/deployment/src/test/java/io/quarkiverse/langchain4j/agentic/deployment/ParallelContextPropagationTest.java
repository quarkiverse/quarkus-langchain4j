package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that CDI request context is propagated to parallel agent worker threads.
 * Without ManagedExecutor, the request context would not be active on the executor's
 * worker threads, causing ContextNotActiveException when accessing @RequestScoped beans.
 */
public class ParallelContextPropagationTest extends OpenAiBaseTest {

    static final AtomicBoolean requestContextWasActive = new AtomicBoolean(false);

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ContextCheckingChatModel.class,
                            ContextCheckingAgent.class, EchoAgent.class,
                            ParallelContextAgent.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));
    @Inject
    ParallelContextAgent agent;

    @Test
    @ActivateRequestContext
    void requestContextPropagatedToParallelWorkerThreads() {
        requestContextWasActive.set(false);
        // The parallel agent runs sub-agents on worker threads.
        // The important assertion is that the request context was active.
        agent.run("test");
        assertThat(requestContextWasActive.get())
                .as("CDI request context should be active on parallel worker thread")
                .isTrue();
    }

    public interface ContextCheckingAgent {
        @ChatModelSupplier
        static ChatModel chatModel() {
            return new ContextCheckingChatModel();
        }

        @UserMessage("Check: {{input}}")
        @Agent(description = "An agent that checks request context on worker thread", outputKey = "check")
        String check(@V("input") String input);
    }

    public interface EchoAgent {
        @ChatModelSupplier
        static ChatModel chatModel() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    return ChatResponse.builder()
                            .aiMessage(new AiMessage("echoed"))
                            .build();
                }
            };
        }

        @UserMessage("Echo: {{input}}")
        @Agent(description = "An agent that echoes input", outputKey = "echo")
        String echo(@V("input") String input);
    }

    public interface ParallelContextAgent {
        @ParallelAgent(outputKey = "result", subAgents = { ContextCheckingAgent.class, EchoAgent.class })
        String run(@V("input") String input);
    }

    /**
     * A ChatModel that checks whether the CDI request context is active during doChat.
     * This runs on the parallel executor's worker thread. If ManagedExecutor is wired,
     * the request context is propagated from the calling thread.
     */
    public static class ContextCheckingChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            boolean active = Arc.container().requestContext().isActive();
            requestContextWasActive.set(active);
            return ChatResponse.builder()
                    .aiMessage(new AiMessage("context-active:" + active))
                    .build();
        }
    }
}
