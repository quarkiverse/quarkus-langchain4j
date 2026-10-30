package io.quarkiverse.langchain4j.gemini.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.jboss.resteasy.reactive.client.SseEvent;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.smallrye.mutiny.Multi;

class GeminiThinkingLevelTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void thinkingLevelSerializesAsLowercaseApiValueAndOmitsNullBudget() throws Exception {
        for (ThinkingLevel level : ThinkingLevel.values()) {
            String json = mapper.writeValueAsString(new ThinkingConfig(null, false, level));
            assertTrue(json.contains("\"thinkingLevel\":\"" + level.toApiValue() + "\""), json);
            assertFalse(json.contains("thinkingBudget"), json);
            assertFalse(json.contains(level.name()), json);
        }
    }

    @Test
    void generationConfigOmitsUnsetSamplingParameters() throws Exception {
        String json = mapper.writeValueAsString(GenerationConfig.builder()
                .maxOutputTokens(128)
                .thinkingConfig(new ThinkingConfig(null, false, ThinkingLevel.HIGH))
                .build());

        assertTrue(json.contains("\"thinkingLevel\":\"high\""), json);
        assertTrue(json.contains("\"maxOutputTokens\":128"), json);
        assertFalse(json.contains("temperature"), json);
        assertFalse(json.contains("topP"), json);
        assertFalse(json.contains("topK"), json);
        assertFalse(json.contains("thinkingBudget"), json);
    }

    @Test
    void generationConfigKeepsExplicitSamplingParameters() throws Exception {
        String json = mapper.writeValueAsString(GenerationConfig.builder()
                .temperature(1.0)
                .topP(0.5)
                .topK(3)
                .thinkingConfig(new ThinkingConfig(256L, false, null))
                .build());

        assertTrue(json.contains("\"temperature\":1.0"), json);
        assertTrue(json.contains("\"topP\":0.5"), json);
        assertTrue(json.contains("\"topK\":3"), json);
        assertTrue(json.contains("\"thinkingBudget\":256"), json);
        assertFalse(json.contains("thinkingLevel"), json);
    }

    @Test
    void chatRequestSendsThinkingLevelAndOmitsUnsetSamplingParameters() throws Exception {
        CapturingChatModel model = new CapturingChatModel(null, null, null, null, false, ThinkingLevel.LOW);
        model.chat(ChatRequest.builder().messages(UserMessage.from("hello")).build());

        String json = json(model.request.generationConfig());
        assertTrue(json.contains("\"thinkingLevel\":\"low\""), json);
        assertFalse(json.contains("temperature"), json);
        assertFalse(json.contains("topP"), json);
        assertFalse(json.contains("topK"), json);
        assertFalse(json.contains("thinkingBudget"), json);
    }

    @Test
    void streamingRequestSendsThinkingLevelAndOmitsUnsetSamplingParameters() throws Exception {
        CapturingStreamingModel model = new CapturingStreamingModel(null, null, null, null, false, ThinkingLevel.MINIMAL);
        model.doChat(ChatRequest.builder().messages(UserMessage.from("hello")).build(), new NoopHandler());

        String json = json(model.request.generationConfig());
        assertTrue(json.contains("\"thinkingLevel\":\"minimal\""), json);
        assertFalse(json.contains("temperature"), json);
        assertFalse(json.contains("topP"), json);
        assertFalse(json.contains("topK"), json);
        assertFalse(json.contains("thinkingBudget"), json);
    }

    @Test
    void rejectsThinkingBudgetTogetherWithThinkingLevel() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new BaseGeminiChatModel("gemini-3.8-flash", null, null, null, null, null, List.of(), 128L, false,
                        ThinkingLevel.HIGH, false));
        assertTrue(exception.getMessage().contains("thinkingBudget"));
        assertTrue(exception.getMessage().contains("thinkingLevel"));
    }

    private String json(Object value) throws JsonProcessingException {
        return mapper.writeValueAsString(value);
    }

    private static GenerateContentResponse sampleResponse() {
        GenerateContentResponse.Candidate.Part part = new GenerateContentResponse.Candidate.Part("ok", null, null, null);
        GenerateContentResponse.Candidate.Content content = new GenerateContentResponse.Candidate.Content(List.of(part));
        GenerateContentResponse.Candidate candidate = new GenerateContentResponse.Candidate(content,
                GenerateContentResponse.FinishReason.STOP);
        return new GenerateContentResponse(List.of(candidate),
                new GenerateContentResponse.UsageMetadata(1, 1, 2, null, null), "gemini-3.8-flash", "id", null);
    }

    private static final class CapturingChatModel extends GeminiChatLanguageModel {

        private GenerateContentRequest request;

        private CapturingChatModel(Double temperature, Integer topK, Double topP, Long thinkingBudget,
                boolean includeThoughts, ThinkingLevel thinkingLevel) {
            super("gemini-3.8-flash", temperature, 128, topK, topP, null, List.of(), thinkingBudget, includeThoughts,
                    thinkingLevel, false);
        }

        @Override
        protected GenerateContentResponse generateContext(GenerateContentRequest request) {
            this.request = request;
            return sampleResponse();
        }
    }

    private static final class CapturingStreamingModel extends GeminiStreamingChatLanguageModel {

        private GenerateContentRequest request;

        private CapturingStreamingModel(Double temperature, Integer topK, Double topP, Long thinkingBudget,
                boolean includeThoughts, ThinkingLevel thinkingLevel) {
            super("gemini-3.8-flash", temperature, 128, topK, topP, null, List.of(), thinkingBudget, includeThoughts,
                    thinkingLevel, false);
        }

        @Override
        protected Multi<SseEvent<String>> generateStreamContext(GenerateContentRequest request) {
            this.request = request;
            return Multi.createFrom().empty();
        }
    }

    private static final class NoopHandler implements StreamingChatResponseHandler {

        @Override
        public void onPartialResponse(String partialResponse) {
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
        }

        @Override
        public void onError(Throwable error) {
        }
    }
}
