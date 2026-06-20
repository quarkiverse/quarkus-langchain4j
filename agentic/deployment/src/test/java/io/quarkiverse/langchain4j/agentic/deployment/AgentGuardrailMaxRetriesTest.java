package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that the maxRetries annotation attribute is respected on agent methods.
 * The guardrail always rejects, so the LLM is called maxRetries times total
 * (upstream treats maxRetries as total attempts, not retries-after-first).
 */
@SuppressWarnings("CdiInjectionPointsInspection")
public class AgentGuardrailMaxRetriesTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(MaxRetriesAgent.class, AlwaysRejectGuardrail.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    MaxRetriesAgent agent;

    @Inject
    AlwaysRejectGuardrail guardrail;

    @Test
    @ActivateRequestContext
    void maxRetriesFromAnnotationIsRespected() {
        assertThatThrownBy(() -> agent.process("hello"))
                .hasRootCauseInstanceOf(dev.langchain4j.guardrail.OutputGuardrailException.class)
                .rootCause()
                .hasMessageContaining("maximum number of retries");

        // maxRetries=2: upstream counts total attempts, so 2 validations total
        assertThat(guardrail.validationCount()).isEqualTo(2);
    }

    public interface MaxRetriesAgent {

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new FixedChatModel();
        }

        @Agent
        @OutputGuardrails(value = AlwaysRejectGuardrail.class, maxRetries = 2)
        @UserMessage("Process: {{request}}")
        String process(@V("request") String request);
    }

    public static class FixedChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(new AiMessage("bad content")).build();
        }
    }

    @ApplicationScoped
    public static class AlwaysRejectGuardrail implements OutputGuardrail {
        private final AtomicInteger count = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            count.incrementAndGet();
            return retry("Content always rejected");
        }

        public int validationCount() {
            return count.get();
        }
    }
}
