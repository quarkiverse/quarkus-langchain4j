package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class SystemClassloaderAgentTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(SystemClassloaderAgent.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    public interface SystemClassloaderAgent {

        @UserMessage("Answer the following question: {{request}}")
        @Agent(description = "A simple agent for testing")
        String ask(@V("request") String request);
    }

    @Inject
    SystemClassloaderAgent agent;

    @Test
    void agentResolvesOnThreadWithSystemClassloader() throws Exception {
        setChatCompletionMessageContent("agent response");

        CompletableFuture<String> future = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                future.complete(agent.ask("hello"));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        thread.setContextClassLoader(ClassLoader.getSystemClassLoader());
        thread.start();

        assertThat(future.get(30, TimeUnit.SECONDS)).isEqualTo("agent response");
    }
}
