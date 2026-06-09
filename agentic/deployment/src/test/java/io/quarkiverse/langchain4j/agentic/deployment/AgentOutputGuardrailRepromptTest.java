package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Verifies that output guardrail reprompting works through the AgentBuilder inner AiServices path.
 * The guardrail rejects the first response and reprompts; the second response is accepted.
 */
@SuppressWarnings("CdiInjectionPointsInspection")
public class AgentOutputGuardrailRepromptTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(RepromptAgent.class, RejectFirstGuardrail.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    RepromptAgent agent;

    @Inject
    RejectFirstGuardrail guardrail;

    @Test
    @ActivateRequestContext
    void outputGuardrailRepromptTriggersRetry() {
        String result = agent.process("hello");

        assertThat(guardrail.validationCount()).isEqualTo(2);
        assertThat(result).isEqualTo("ACCEPTED");
    }

    public interface RepromptAgent {

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new RetryAwareChatModel();
        }

        @Agent
        @OutputGuardrails(RejectFirstGuardrail.class)
        @UserMessage("Process: {{request}}")
        String process(@V("request") String request);
    }

    public static class RetryAwareChatModel implements ChatModel {
        private final AtomicInteger callCount = new AtomicInteger(0);

        @Override
        public ChatResponse doChat(ChatRequest request) {
            int call = callCount.incrementAndGet();
            String response = call == 1 ? "REJECTED_CONTENT" : "ACCEPTED";
            return ChatResponse.builder().aiMessage(new AiMessage(response)).build();
        }
    }

    @ApplicationScoped
    public static class RejectFirstGuardrail implements OutputGuardrail {
        private final AtomicInteger count = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            int attempt = count.incrementAndGet();
            if (responseFromLLM.text().equals("REJECTED_CONTENT")) {
                return reprompt("Content rejected, please try again",
                        "Please respond with ACCEPTED instead");
            }
            return success();
        }

        public int validationCount() {
            return count.get();
        }
    }
}
