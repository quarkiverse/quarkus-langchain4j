package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.enterprise.context.SessionScoped;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.UserMessage;
import io.quarkus.test.QuarkusUnitTest;

public class CdiScopeValidationSessionScopedTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(SessionScopedRetriever.class,
                                    AgentUsingRetriever.class,
                                    Agents.FixedResponseChatModel.class))
            .assertException(t -> assertThat(t.getMessage())
                    .contains("@SessionScoped")
                    .contains("cannot be auto-wired"));

    @SessionScoped
    public static class SessionScopedRetriever implements ContentRetriever {

        @Override
        public List<Content> retrieve(Query query) {
            return List.of(Content.from(TextSegment.from("test")));
        }
    }

    public interface AgentUsingRetriever {

        @UserMessage("test")
        @Agent(description = "Agent", outputKey = "answer")
        String ask();

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("response");
        }
    }

    @Test
    void sessionScopedBeanRejectedAtBuildTime() {
        // assertException on the extension handles verification
    }
}
