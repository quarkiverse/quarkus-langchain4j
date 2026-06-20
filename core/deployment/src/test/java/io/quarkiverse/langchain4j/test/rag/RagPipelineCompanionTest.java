package io.quarkiverse.langchain4j.test.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import io.quarkiverse.langchain4j.RagPipeline;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests for {@link RagPipeline} in companion mode — where the annotation sits
 * on the same interface as {@link RegisterAiService}.
 */
public class RagPipelineCompanionTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            SingleRetrieverService.class,
                            MultiRetrieverService.class,
                            RouterService.class,
                            PreBuiltService.class,
                            FullPipelineService.class,
                            AggregatorService.class,
                            TestRetrieverA.class,
                            TestRetrieverB.class,
                            ConditionalRouter.class,
                            PreBuiltAugmentor.class,
                            MarkerTransformer.class,
                            MarkerInjector.class,
                            TopOneAggregator.class,
                            EchoChatModel.class));

    @Inject
    SingleRetrieverService singleRetrieverService;

    @Inject
    MultiRetrieverService multiRetrieverService;

    @Inject
    RouterService routerService;

    @Inject
    PreBuiltService preBuiltService;

    @Inject
    FullPipelineService fullPipelineService;

    @Inject
    AggregatorService aggregatorService;

    // -- Scenario 1: Single retriever --

    @RegisterAiService(chatMemoryProvider = void.class)
    @RagPipeline(retrievers = { TestRetrieverA.class })
    public interface SingleRetrieverService {
        String chat(@dev.langchain4j.service.UserMessage String message);
    }

    @Test
    @ActivateRequestContext
    void singleRetriever_shouldAugmentWithRetrievedContent() {
        String response = singleRetrieverService.chat("hello");

        assertThat(response).contains("content from retriever A");
    }

    // -- Scenario 2: Multiple retrievers (auto DefaultQueryRouter) --

    @RegisterAiService(chatMemoryProvider = void.class)
    @RagPipeline(retrievers = { TestRetrieverA.class, TestRetrieverB.class })
    public interface MultiRetrieverService {
        String chat(@dev.langchain4j.service.UserMessage String message);
    }

    @Test
    @ActivateRequestContext
    void multipleRetrievers_shouldAugmentWithAllContent() {
        String response = multiRetrieverService.chat("hello");

        assertThat(response).contains("content from retriever A");
        assertThat(response).contains("content from retriever B");
    }

    // -- Scenario 3: Explicit router --

    @RegisterAiService(chatMemoryProvider = void.class)
    @RagPipeline(router = ConditionalRouter.class)
    public interface RouterService {
        String chat(@dev.langchain4j.service.UserMessage String message);
    }

    @Test
    @ActivateRequestContext
    void explicitRouter_shouldRouteToCorrectRetriever() {
        String response = routerService.chat("hello");

        // ConditionalRouter always routes to TestRetrieverB
        assertThat(response).contains("content from retriever B");
    }

    // -- Scenario 4: Pre-built augmentor --

    @RegisterAiService(chatMemoryProvider = void.class)
    @RagPipeline(augmentor = PreBuiltAugmentor.class)
    public interface PreBuiltService {
        String chat(@dev.langchain4j.service.UserMessage String message);
    }

    @Test
    @ActivateRequestContext
    void preBuiltAugmentor_shouldUseCustomAugmentation() {
        String response = preBuiltService.chat("hello");

        assertThat(response).isEqualTo("pre-built augmented response");
    }

    // -- Supporting beans --

    @ApplicationScoped
    public static class TestRetrieverA implements ContentRetriever {
        @Override
        public List<Content> retrieve(Query query) {
            // When a transformer ran, the query text ends with "-transformed"
            String suffix = query.text().endsWith("-transformed") ? " (transformed)" : "";
            return List.of(Content.from("content from retriever A" + suffix));
        }
    }

    @ApplicationScoped
    public static class TestRetrieverB implements ContentRetriever {
        @Override
        public List<Content> retrieve(Query query) {
            return List.of(Content.from("content from retriever B"));
        }
    }

    @ApplicationScoped
    public static class ConditionalRouter implements QueryRouter {
        private final TestRetrieverB retrieverB;

        @Inject
        ConditionalRouter(TestRetrieverB retrieverB) {
            this.retrieverB = retrieverB;
        }

        @Override
        public Collection<ContentRetriever> route(Query query) {
            return List.of(retrieverB);
        }
    }

    @ApplicationScoped
    public static class PreBuiltAugmentor implements RetrievalAugmentor {
        @Override
        public AugmentationResult augment(AugmentationRequest request) {
            return new AugmentationResult(
                    UserMessage.userMessage("pre-built augmented response"),
                    List.of(Content.from("pre-built content")));
        }
    }

    // -- Scenario 5: Full decomposed pipeline (transformer + injector) --

    @RegisterAiService(chatMemoryProvider = void.class)
    @RagPipeline(retrievers = { TestRetrieverA.class }, transformer = MarkerTransformer.class, injector = MarkerInjector.class)
    public interface FullPipelineService {
        String chat(@dev.langchain4j.service.UserMessage String message);
    }

    @Test
    @ActivateRequestContext
    void fullPipeline_transformerAndInjectorApplied() {
        String response = fullPipelineService.chat("hello");

        // MarkerTransformer tags the query; TestRetrieverA checks for the tag and includes it
        // in returned content; MarkerInjector wraps the content as "[injected: ...]"
        assertThat(response).contains("[injected: content from retriever A (transformed)]");
    }

    // -- Scenario 6: Custom aggregator filters results --

    @RegisterAiService(chatMemoryProvider = void.class)
    @RagPipeline(retrievers = { TestRetrieverA.class, TestRetrieverB.class }, aggregator = TopOneAggregator.class)
    public interface AggregatorService {
        String chat(@dev.langchain4j.service.UserMessage String message);
    }

    @Test
    @ActivateRequestContext
    void customAggregator_keepsOnlyFirstResult() {
        String response = aggregatorService.chat("hello");

        // TopOneAggregator keeps only the first content item across all retrievers
        // TestRetrieverA is listed first so its content appears; TestRetrieverB's is dropped
        assertThat(response).contains("content from retriever A");
        assertThat(response).doesNotContain("content from retriever B");
    }

    // -- Supporting beans for full pipeline and aggregator scenarios --

    /**
     * Appends "-transformed" to the query text so downstream retrievers can detect
     * that the transformer ran.
     */
    @ApplicationScoped
    public static class MarkerTransformer implements QueryTransformer {
        @Override
        public java.util.Collection<Query> transform(Query query) {
            return List.of(new Query(query.text() + "-transformed", query.metadata()));
        }
    }

    /**
     * Wraps each injected content segment as "[injected: <text>]" so tests can
     * assert the injector was actually called.
     */
    @ApplicationScoped
    public static class MarkerInjector implements ContentInjector {
        @Override
        public ChatMessage inject(List<Content> contents, ChatMessage chatMessage) {
            String injected = contents.stream()
                    .map(c -> "[injected: " + c.textSegment().text() + "]")
                    .collect(java.util.stream.Collectors.joining(" "));
            String original = ((UserMessage) chatMessage).singleText();
            return UserMessage.userMessage(original + "\n" + injected);
        }
    }

    /**
     * Keeps only the first content item across all query-retriever pairs, discarding
     * the rest. Tests that a custom aggregator is actually invoked.
     */
    @ApplicationScoped
    public static class TopOneAggregator implements ContentAggregator {
        @Override
        public List<Content> aggregate(Map<Query, java.util.Collection<List<Content>>> queryToContents) {
            return queryToContents.values().stream()
                    .flatMap(java.util.Collection::stream)
                    .flatMap(List::stream)
                    .limit(1)
                    .toList();
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
