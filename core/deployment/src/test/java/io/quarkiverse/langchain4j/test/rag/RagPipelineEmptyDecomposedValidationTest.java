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
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import io.quarkiverse.langchain4j.RagPipeline;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Validates that {@link RagPipeline} rejects empty decomposed mode:
 * at least one retriever or a router must be specified in decomposed mode.
 */
class RagPipelineEmptyDecomposedValidationTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            InvalidEmptyDecomposedService.class,
                            DummyChatModel.class,
                            DummyTransformer.class))
            .assertException(t -> {
                if (!t.getMessage().contains("At least one retriever or a router must be specified")) {
                    throw new AssertionError(
                            "Expected IllegalStateException with message about empty decomposed mode, but got: "
                                    + t.getMessage(),
                            t);
                }
            });

    @Test
    void testEmptyDecomposedFailsDeployment() {
        fail("Should not reach here — deployment should have failed");
    }

    @RegisterAiService(chatMemoryProvider = void.class)
    @RagPipeline(transformer = DummyTransformer.class)
    public interface InvalidEmptyDecomposedService {
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
    static class DummyTransformer implements QueryTransformer {
        @Override
        public Collection<Query> transform(Query query) {
            return List.of(query);
        }
    }
}
