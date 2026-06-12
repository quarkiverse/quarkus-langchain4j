package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that inner-class agents (e.g. {@code TestClass$InnerAgent}) resolve
 * correctly even when invoked from threads with a non-Quarkus TCCL.
 * <p>
 * In production and dev mode, agent creation and invocation can happen on Vert.x
 * I/O threads or virtual threads spawned by {@code Executors.newVirtualThreadPerTaskExecutor()},
 * where the TCCL is the system classloader — not the Quarkus augmentation classloader.
 * Inner classes are invisible to the system classloader, so TCCL-only class loading fails
 * silently with {@code ClassNotFoundException}.
 * <p>
 * This test simulates that scenario by invoking the agent from a separate thread with
 * the TCCL explicitly set to the system classloader. The convoluted thread setup is
 * necessary because {@code @QuarkusTest} sets the TCCL correctly on the test thread —
 * the real-world failure only manifests on threads that Quarkus doesn't control.
 */
public class InnerClassAgentClassloaderTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(InnerAgent.class, Agents.FixedResponseChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    InnerAgent agent;

    @Test
    void innerClassAgentResolvesFromQuarkusTestThread() {
        String result = agent.greet("hello");
        assertThat(result).isEqualTo("fixed-response");
    }

    @Test
    void innerClassAgentResolvesFromThreadWithSystemClassloader() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            String result = CompletableFuture.supplyAsync(() -> {
                ClassLoader original = Thread.currentThread().getContextClassLoader();
                try {
                    Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
                    return agent.greet("hello");
                } finally {
                    Thread.currentThread().setContextClassLoader(original);
                }
            }, executor).get();

            assertThat(result).isEqualTo("fixed-response");
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    public interface InnerAgent {
        @UserMessage("{{input}}")
        @Agent(description = "Inner class agent for classloader test", outputKey = "greeting")
        String greet(String input);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("fixed-response");
        }
    }
}
