package io.quarkiverse.langchain4j.test.rag;

import static org.junit.jupiter.api.Assertions.fail;

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
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import io.quarkiverse.langchain4j.RagPipeline;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Validates that {@link RagPipeline} rejects invalid mode conflict:
 * pre-built {@code augmentor} attribute cannot be combined with decomposed
 * pipeline attributes ({@code retrievers}, {@code router}, {@code transformer}).
 */
class RagPipelineModeConflictValidationTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            InvalidModeConflictService.class,
                            DummyChatModel.class,
                            DummyRetriever.class,
                            DummyAugmentor.class))
            .assertException(t -> {
                if (!t.getMessage()
                        .contains("Pre-built augmentor mode cannot be combined with decomposed pipeline attributes")) {
                    throw new AssertionError(
                            "Expected IllegalStateException with message about mode conflict, but got: " + t.getMessage(),
                            t);
                }
            });

    @Test
    void testModeConflictFailsDeployment() {
        fail("Should not reach here — deployment should have failed");
    }

    @RegisterAiService(chatMemoryProvider = void.class)
    @RagPipeline(augmentor = DummyAugmentor.class, retrievers = { DummyRetriever.class })
    public interface InvalidModeConflictService {
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
    static class DummyAugmentor implements RetrievalAugmentor {
        @Override
        public AugmentationResult augment(AugmentationRequest request) {
            return AugmentationResult.builder()
                    .chatMessage(request.chatMessage())
                    .contents(List.of())
                    .build();
        }
    }
}
