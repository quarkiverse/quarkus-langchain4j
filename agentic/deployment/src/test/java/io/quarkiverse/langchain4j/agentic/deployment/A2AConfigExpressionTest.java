package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.enterprise.inject.CreationException;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.V;
import io.quarkus.test.QuarkusUnitTest;

public class A2AConfigExpressionTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ExpressionA2AAgent.class, OrchestratorAgent.class,
                            DummyChatModel.class))
            .overrideRuntimeConfigKey("remote.agent.url", "http://resolved-from-config:9999")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url", "http://localhost");

    @Inject
    OrchestratorAgent orchestrator;

    @Test
    void configExpressionInA2AUrlIsResolved() {
        // The A2A agent uses ${remote.agent.url} which should resolve to
        // http://resolved-from-config:9999. The agent creation will fail
        // trying to fetch the agent card from that URL. The A2A SDK does not
        // include the resolved URL in its error messages, so we verify that
        // the expression was resolved by confirming the error is a network-level
        // failure (UnresolvedAddressException — the SDK tried to connect) rather
        // than a URI syntax error (which would mean the raw ${...} was passed).
        CreationException ex = assertThrows(CreationException.class,
                () -> orchestrator.run("test"));
        // If the expression were NOT resolved, the error would be URISyntaxException
        // with "${remote.agent.url}" in the message. After resolution, the SDK
        // attempts an actual HTTP connection to "resolved-from-config:9999" and
        // fails with UnresolvedAddressException (no such host).
        assertThat(ex).hasStackTraceContaining("UnresolvedAddressException");
        // Confirm the raw expression is NOT in the stack trace — proves resolution happened
        assertThat(ex.getMessage()).doesNotContain("${remote.agent.url}");
        Throwable cause = ex;
        while (cause != null) {
            assertThat(cause.getMessage() == null ? "" : cause.getMessage())
                    .doesNotContain("${remote.agent.url}");
            cause = cause.getCause();
        }
    }

    public interface ExpressionA2AAgent {
        @A2AClientAgent(a2aServerUrl = "${remote.agent.url}", description = "Remote agent", outputKey = "result")
        String call(@V("input") String input);

        @ChatModelSupplier
        static ChatModel model() {
            return new DummyChatModel();
        }
    }

    public interface OrchestratorAgent {
        @SequenceAgent(outputKey = "result", subAgents = { ExpressionA2AAgent.class })
        String run(@V("input") String input);
    }

    public static class DummyChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(new AiMessage("ok")).build();
        }
    }
}
