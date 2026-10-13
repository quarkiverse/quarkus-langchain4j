package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.ref.Cleaner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * An agent whose first invocation happens on a thread owned by the JVM itself: a
 * {@link java.lang.ref.Cleaner} cleanup action runs on a {@code jdk.internal.misc.InnocuousThread},
 * a thread the JDK creates and whose context class loader the JDK itself sets to the system class
 * loader, no matter which thread calls {@code Cleaner.create()}. No test code manipulates any
 * thread's context class loader.
 */
public class CleanerCallbackAgentTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(CleanerCallbackAgent.class, FixedResponseChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    public interface CleanerCallbackAgent {

        @UserMessage("Answer the following question: {{request}}")
        @Agent(description = "A simple agent for testing")
        String ask(@V("request") String request);

        // in-process model: the point of the test is the agent/AI-service machinery on the JVM-owned
        // thread, not the HTTP stack
        @ChatModelSupplier
        static ChatModel chatModel() {
            return new FixedResponseChatModel("agent response");
        }
    }

    public static class FixedResponseChatModel implements ChatModel {

        private final String response;

        public FixedResponseChatModel(String response) {
            this.response = response;
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(new AiMessage(response)).build();
        }
    }

    @Inject
    CleanerCallbackAgent agent;

    @Test
    void agentIsResolvedWhenFirstInvokedFromAJdkOwnedThread() throws Exception {
        Invocation invocation = new Invocation();
        registerCleanupAction(Cleaner.create(), invocation);

        for (int i = 0; i < 50 && invocation.completed.getCount() > 0; i++) {
            System.gc();
            invocation.completed.await(200, TimeUnit.MILLISECONDS);
        }

        assertThat(invocation.completed.getCount()).as("cleaner action did not run").isZero();
        assertThat(invocation.systemClassLoader).as("cleaner thread '%s' did not run with the system classloader",
                invocation.threadName).isTrue();
        assertThat(invocation.failure).isNull();
        assertThat(invocation.result).isEqualTo("agent response");
    }

    private void registerCleanupAction(Cleaner cleaner, Invocation invocation) {
        Object resource = new Object();
        cleaner.register(resource, () -> {
            Thread thread = Thread.currentThread();
            invocation.threadName = thread.getName();
            invocation.systemClassLoader = thread.getContextClassLoader() == ClassLoader.getSystemClassLoader();
            try {
                invocation.result = agent.ask("hello");
            } catch (Throwable t) {
                invocation.failure = t;
            } finally {
                invocation.completed.countDown();
            }
        });
    }

    static class Invocation {
        final CountDownLatch completed = new CountDownLatch(1);
        volatile String threadName;
        volatile boolean systemClassLoader;
        volatile String result;
        volatile Throwable failure;
    }
}
