package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

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
 * Verifies F-5: @Transactional + @Retry on the same agent method emits a build-time
 * warning but does NOT fail the build.
 * Warning text is emitted to the build log only (not assertable via QuarkusUnitTest).
 */
public class TransactionalRetryWarningTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TransactionalRetryAgent.class, Agents.FixedResponseChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    public interface TransactionalRetryAgent {
        @UserMessage("{{q}}")
        @Agent(description = "Agent with transactional+retry combo")
        @Transactional
        @Retry(maxRetries = 1)
        String answer(String q);

        @ChatModelSupplier
        static ChatModel model() {
            return new Agents.FixedResponseChatModel("ok");
        }
    }

    @Inject
    TransactionalRetryAgent agent;

    @Test
    void agentBootsSuccessfullyDespiteTransactionalRetryCombo() {
        // F-5 is a warning, not an error.
        assertThat(agent).isNotNull();
    }
}
