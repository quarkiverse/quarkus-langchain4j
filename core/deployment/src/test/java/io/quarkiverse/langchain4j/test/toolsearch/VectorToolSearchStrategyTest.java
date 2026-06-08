package io.quarkiverse.langchain4j.test.toolsearch;

import static dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import dev.langchain4j.service.tool.search.vector.VectorToolSearchStrategy;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

/**
 * The built-in {@code VectorToolSearchStrategy} must work through the {@code @RegisterAiService} pickup: given a query
 * matching the booking tool, the vector search surfaces it (and not the weather distractor) so it can be executed.
 */
public class VectorToolSearchStrategyTest {

    static final String SEARCH_TOOL_NAME = "tool_search_tool";
    static final String FOUND_TOOL_NAME = "getBookingDetails";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(BookingTools.class,
                            WeatherTools.class,
                            StrategyProducer.class,
                            ServiceWithVectorToolSearch.class));

    @RegisterAiService(tools = { BookingTools.class, WeatherTools.class }, chatLanguageModelSupplier = ModelSupplier.class)
    interface ServiceWithVectorToolSearch {

        String chat(@UserMessage String msg, @MemoryId Object id);

    }

    @Inject
    ServiceWithVectorToolSearch service;

    @Test
    @ActivateRequestContext
    void vectorSearchedToolIsExecuted() {
        String answer = service.chat("get my booking details", 1);
        assertEquals("REAL_TOOL_RESULT", answer);
    }

    @ApplicationScoped
    public static class StrategyProducer {
        @Produces
        @ApplicationScoped
        public ToolSearchStrategy toolSearchStrategy() {
            return VectorToolSearchStrategy.builder()
                    .embeddingModel(new KeywordEmbeddingModel())
                    .maxResults(1)
                    .build();
        }
    }

    @ApplicationScoped
    public static class WeatherTools {
        @Tool("Returns the weather forecast")
        public String getWeather() {
            return "WEATHER_TOOL_RESULT";
        }
    }

    public static class ModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new Model();
        }
    }

    /**
     * Calls the built-in {@code tool_search_tool} with a natural language query and then invokes whichever tool the
     * vector search surfaced. If the search returned the wrong tool, the booking tool would not be available.
     */
    public static class Model implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            List<ChatMessage> messages = chatRequest.messages();
            ChatMessage lastMessage = messages.get(messages.size() - 1);

            if (lastMessage.type().equals(TOOL_EXECUTION_RESULT)) {
                ToolExecutionResultMessage result = (ToolExecutionResultMessage) lastMessage;
                if (result.text().contains("REAL_TOOL_RESULT")) {
                    return answer("REAL_TOOL_RESULT");
                }
                if (hasTool(chatRequest, FOUND_TOOL_NAME)) {
                    return callTool(FOUND_TOOL_NAME, null);
                }
                return answer("FOUND_TOOL_NOT_AVAILABLE");
            }

            if (hasTool(chatRequest, FOUND_TOOL_NAME)) {
                return answer("TOOL_NOT_NARROWED");
            }
            if (hasTool(chatRequest, SEARCH_TOOL_NAME)) {
                return callTool(SEARCH_TOOL_NAME, "{\"query\":\"booking details\"}");
            }
            return answer("NO_SEARCH_TOOL");
        }

        private static boolean hasTool(ChatRequest chatRequest, String name) {
            List<ToolSpecification> toolSpecifications = chatRequest.toolSpecifications();
            if (toolSpecifications == null) {
                return false;
            }
            return toolSpecifications.stream().anyMatch(spec -> spec.name().equals(name));
        }

        private static ChatResponse callTool(String name, String arguments) {
            ToolExecutionRequest.Builder builder = ToolExecutionRequest.builder().name(name).id(name);
            if (arguments != null) {
                builder.arguments(arguments);
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(builder.build()))
                    .finishReason(FinishReason.TOOL_EXECUTION)
                    .build();
        }

        private static ChatResponse answer(String text) {
            return ChatResponse.builder().aiMessage(new AiMessage(text)).build();
        }
    }

    /**
     * Deterministic, dependency-free {@link EmbeddingModel}: embeds text as a bag-of-words vector over a fixed
     * vocabulary, so cosine similarity is high when texts share vocabulary terms.
     */
    public static class KeywordEmbeddingModel implements EmbeddingModel {

        private static final List<String> VOCABULARY = List.of("booking", "weather");

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            List<Embedding> embeddings = segments.stream()
                    .map(segment -> Embedding.from(toVector(segment.text())))
                    .toList();
            return Response.from(embeddings);
        }

        private static float[] toVector(String text) {
            String normalized = text.toLowerCase(Locale.ROOT);
            float[] vector = new float[VOCABULARY.size()];
            for (int i = 0; i < VOCABULARY.size(); i++) {
                vector[i] = normalized.contains(VOCABULARY.get(i)) ? 1.0f : 0.0f;
            }
            return vector;
        }
    }
}
