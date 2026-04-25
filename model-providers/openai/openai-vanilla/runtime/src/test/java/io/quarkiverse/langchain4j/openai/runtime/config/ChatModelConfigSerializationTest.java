package io.quarkiverse.langchain4j.openai.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Regression test for GitHub issue #2369: topP() and temperature() should be
 * OptionalDouble so they can be omitted from JSON serialization when not set.
 * This prevents "temperature and top_p cannot both be specified" errors with
 * OpenAI-compatible gateways that don't support top_p.
 */
class ChatModelConfigSerializationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    @Test
    void topPAbsent_shouldNotSerializeTopP() throws JsonProcessingException {
        ChatModelConfig config = new TestChatModelConfig(null, null);

        String json = MAPPER.writeValueAsString(config);

        assertThat(json)
                .doesNotContain("top_p")
                .doesNotContain("topP")
                .doesNotContain("temperature");
    }

    @Test
    void topPSet_shouldSerializeTopP() throws JsonProcessingException {
        ChatModelConfig config = new TestChatModelConfig(OptionalDouble.of(0.8), OptionalDouble.of(0.9));

        String json = MAPPER.writeValueAsString(config);

        assertThat(json)
                .contains("\"top_p\":0.9")
                .contains("\"temperature\":0.8");
    }

    @Test
    void topPOnly_shouldOnlySerializeTopP() throws JsonProcessingException {
        // temperature is absent, only topP is set
        ChatModelConfig config = new TestChatModelConfig(null, OptionalDouble.of(0.7));

        String json = MAPPER.writeValueAsString(config);

        assertThat(json)
                .contains("\"top_p\":0.7")
                .doesNotContain("\"temperature\":");
    }

    @Test
    void temperatureOnly_shouldOnlySerializeTemperature() throws JsonProcessingException {
        // topP is absent, only temperature is set
        ChatModelConfig config = new TestChatModelConfig(OptionalDouble.of(0.5), null);

        String json = MAPPER.writeValueAsString(config);

        assertThat(json)
                .contains("\"temperature\":0.5")
                .doesNotContain("\"top_p\":");
    }

    /**
     * Minimal implementation of ChatModelConfig for testing JSON serialization.
     * Only includes the fields relevant to issue #2369.
     */
    static class TestChatModelConfig implements ChatModelConfig {
        private final OptionalDouble temperature;
        private final OptionalDouble topP;

        TestChatModelConfig(OptionalDouble temperature, OptionalDouble topP) {
            this.temperature = temperature;
            this.topP = topP;
        }

        @Override
        public OptionalDouble temperature() {
            return temperature;
        }

        @Override
        public OptionalDouble topP() {
            return topP;
        }

        // Stub all other methods
        @Override
        public String modelName() {
            return "gpt-4o-mini";
        }

        @Override
        public Optional<Integer> maxTokens() {
            return Optional.empty();
        }

        @Override
        public Optional<Integer> maxCompletionTokens() {
            return Optional.empty();
        }

        @Override
        public Double presencePenalty() {
            return 0.0;
        }

        @Override
        public Double frequencyPenalty() {
            return 0.0;
        }

        @Override
        public Optional<Boolean> logRequests() {
            return Optional.empty();
        }

        @Override
        public Optional<Boolean> logResponses() {
            return Optional.empty();
        }

        @Override
        public Optional<String> responseFormat() {
            return Optional.empty();
        }

        @Override
        public Optional<Boolean> strictJsonSchema() {
            return Optional.empty();
        }

        @Override
        public Optional<java.util.List<String>> stop() {
            return Optional.empty();
        }

        @Override
        public Optional<String> reasoningEffort() {
            return Optional.empty();
        }

        @Override
        public Optional<String> serviceTier() {
            return Optional.empty();
        }
    }
}
