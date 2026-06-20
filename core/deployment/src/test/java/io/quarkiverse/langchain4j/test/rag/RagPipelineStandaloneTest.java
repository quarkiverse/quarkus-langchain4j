package io.quarkiverse.langchain4j.test.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import io.quarkiverse.langchain4j.RagPipeline;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests for {@link RagPipeline} in standalone mode — where the annotation sits
 * on a separate interface (not an AI service) and produces a reusable
 * {@link dev.langchain4j.rag.RetrievalAugmentor} CDI bean that multiple
 * AI services can reference.
 */
public class RagPipelineStandaloneTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            SharedRag.class,
                            Assistant1.class,
                            Assistant2.class,
                            TestRetriever.class,
                            EchoChatModel.class));

    @Inject
    Assistant1 assistant1;

    @Inject
    Assistant2 assistant2;

    // -- Standalone pipeline — NOT an AI service --

    @RagPipeline(retrievers = { TestRetriever.class })
    public interface SharedRag {
    }

    // -- Two AI services sharing the same pipeline --

    @RegisterAiService(chatMemoryProvider = void.class)
    @RagPipeline(augmentor = SharedRag.class)
    public interface Assistant1 {
        String chat(@dev.langchain4j.service.UserMessage String message);
    }

    @RegisterAiService(chatMemoryProvider = void.class)
    @RagPipeline(augmentor = SharedRag.class)
    public interface Assistant2 {
        String chat(@dev.langchain4j.service.UserMessage String message);
    }

    @Test
    @ActivateRequestContext
    void assistant1_shouldUseSharedRagPipeline() {
        String response = assistant1.chat("hello");

        assertThat(response).contains("shared RAG content");
    }

    @Test
    @ActivateRequestContext
    void assistant2_shouldUseSharedRagPipeline() {
        String response = assistant2.chat("hello");

        assertThat(response).contains("shared RAG content");
    }

    // -- Supporting beans --

    @ApplicationScoped
    public static class TestRetriever implements ContentRetriever {
        @Override
        public List<Content> retrieve(Query query) {
            return List.of(Content.from("shared RAG content"));
        }
    }

    /**
     * Echoes back the last user message text. Since RAG content injection modifies
     * the user message, the response will contain the augmented text.
     */
    @ApplicationScoped
    public static class EchoChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            String text = ((UserMessage) request.messages().get(request.messages().size() - 1)).singleText();
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(text))
                    .build();
        }
    }
}
