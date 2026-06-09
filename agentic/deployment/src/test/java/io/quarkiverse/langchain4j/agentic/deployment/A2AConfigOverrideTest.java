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

public class A2AConfigOverrideTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RemoteWriterAgent.class, OrchestratorAgent.class,
                            DummyChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.agent.\"remote-writer\".a2a-server-url",
                    "http://localhost:9999")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url", "http://localhost");

    @Inject
    OrchestratorAgent orchestrator;

    @Test
    void perAgentNamedConfigOverridesAnnotationUrl() {
        // The A2A agent uses an invalid URL in the annotation:
        //   @A2AClientAgent(a2aServerUrl = "invalid://bad-url", name = "remote-writer", ...)
        // Config overrides it with Priority 1:
        //   quarkus.langchain4j.agent."remote-writer".a2a-server-url=http://localhost:9999
        // The agent creation will fail trying to fetch the agent card from the CONFIG URL.
        // We verify the config URL was used (not the annotation URL) by confirming the error
        // is ConnectException (valid URL, server not running), not an exception about
        // "invalid:" protocol (which would mean the annotation URL was used).
        CreationException ex = assertThrows(CreationException.class,
                () -> orchestrator.run("test"));
        // The SDK attempts to connect to "localhost:9999" and fails with ConnectException.
        // If the annotation URL were used, we'd get "unknown protocol: invalid" or similar.
        assertThat(ex).hasStackTraceContaining("ConnectException");
        // Confirm the annotation's invalid protocol is NOT mentioned — proves override happened
        assertThat(ex.getMessage()).doesNotContain("invalid:");
        assertThat(ex.getMessage()).doesNotContain("bad-url");
        Throwable cause = ex;
        while (cause != null) {
            String msg = cause.getMessage() == null ? "" : cause.getMessage();
            assertThat(msg).doesNotContain("invalid:");
            assertThat(msg).doesNotContain("bad-url");
            cause = cause.getCause();
        }
    }

    public interface RemoteWriterAgent {
        @A2AClientAgent(a2aServerUrl = "invalid://bad-url", name = "remote-writer", description = "Remote agent", outputKey = "result")
        String call(@V("input") String input);

        @ChatModelSupplier
        static ChatModel model() {
            return new DummyChatModel();
        }
    }

    public interface OrchestratorAgent {
        @SequenceAgent(outputKey = "result", subAgents = { RemoteWriterAgent.class })
        String run(@V("input") String input);
    }

    public static class DummyChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(new AiMessage("ok")).build();
        }
    }
}
