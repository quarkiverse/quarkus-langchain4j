package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies F-4: @Retry(retryOn=...) with types that won't match AgentInvocationException
 * emits a build-time warning but does NOT fail the build.
 * Warning text is emitted to the build log only (not assertable via QuarkusUnitTest).
 */
public class FaultToleranceRetryWarningTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(NarrowRetryAgent.class, Agents.FixedResponseChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    public interface NarrowRetryAgent {
        @UserMessage("{{q}}")
        @Agent(description = "Agent with narrow retryOn")
        @Retry(maxRetries = 1, retryOn = IllegalStateException.class)
        String answer(String q);

        @ChatModelSupplier
        static ChatModel model() {
            return new Agents.FixedResponseChatModel("ok");
        }
    }

    @Inject
    NarrowRetryAgent agent;

    @Test
    void agentBootsSuccessfullyDespiteNarrowRetryOn() {
        // F-4 is a warning, not an error — the app should boot and function.
        assertThat(agent).isNotNull();
    }
}
