package io.quarkiverse.langchain4j.test.rag;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.Collection;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import io.quarkiverse.langchain4j.RagPipeline;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Validates that {@link RagPipeline} rejects router + retrievers conflict:
 * cannot specify both {@code router} and {@code retrievers} attributes.
 */
class RagPipelineRouterRetrieverConflictValidationTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            InvalidRouterRetrieverConflictService.class,
                            DummyChatModel.class,
                            DummyRetriever.class,
                            DummyRouter.class))
            .assertException(t -> {
                if (!t.getMessage().contains("Cannot specify both router and retrievers")) {
                    throw new AssertionError(
                            "Expected IllegalStateException with message about router+retrievers conflict, but got: "
                                    + t.getMessage(),
                            t);
                }
            });

    @Test
    void testRouterRetrieverConflictFailsDeployment() {
        fail("Should not reach here — deployment should have failed");
    }

    @RegisterAiService(chatMemoryProvider = void.class)
    @RagPipeline(router = DummyRouter.class, retrievers = { DummyRetriever.class })
    public interface InvalidRouterRetrieverConflictService {
        String chat(String msg);
    }

    @ApplicationScoped
    static class DummyChatModel implements ChatModel {
        @Override
        public ChatResponse chat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(AiMessage.from("test")).build();
        }
    }

    @ApplicationScoped
    static class DummyRetriever implements ContentRetriever {
        @Override
        public List<Content> retrieve(Query query) {
            return List.of(Content.from("dummy"));
        }
    }

    @ApplicationScoped
    static class DummyRouter implements QueryRouter {
        @Override
        public Collection<ContentRetriever> route(Query query) {
            return List.of();
        }
    }
}
