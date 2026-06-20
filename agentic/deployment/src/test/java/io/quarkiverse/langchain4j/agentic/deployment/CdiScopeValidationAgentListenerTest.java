package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.RequestScoped;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.UserMessage;
import io.quarkus.test.QuarkusUnitTest;

public class CdiScopeValidationAgentListenerTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(RequestScopedAgentListener.class,
                                    SimpleAgent.class,
                                    Agents.FixedResponseChatModel.class))
            .assertException(t -> assertThat(t.getMessage())
                    .contains("@RequestScoped")
                    .contains("cannot be auto-wired"));

    @RequestScoped
    public static class RequestScopedAgentListener implements AgentListener {
        // No methods need to be implemented - just the marker interface
    }

    public interface SimpleAgent {

        @UserMessage("test")
        @Agent(description = "Agent", outputKey = "answer")
        String ask();

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("response");
        }
    }

    @Test
    void requestScopedAgentListenerRejectedAtBuildTime() {
        // assertException on the extension handles verification
    }
}
