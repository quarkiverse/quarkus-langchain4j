package io.quarkiverse.langchain4j.jitllm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.beehive.jitllm.api.ModelCapabilities;
import org.beehive.jitllm.api.ReasoningEffort;
import org.junit.jupiter.api.Test;

/**
 * Settings the holder resolves before anything is loaded. Constructing a holder touches no model and
 * no device, so none is needed here.
 */
class JitLLMModelHolderTest {

    private static JitLLMModelHolder unconfigured(String modelName) {
        return new JitLLMModelHolder(Optional.empty(), modelName, "Q8_0",
                null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void engineChoosesThePrefillAndThinkingIsOffWhenNothingIsConfigured() {
        JitLLMModelHolder holder = unconfigured(Consts.DEFAULT_CHAT_MODEL_NAME);

        assertThat(holder.withPrefillDecode).isFalse();
        assertThat(holder.prefillBatchSize).isEqualTo(1);
        assertThat(holder.enableThinking).isFalse();
        assertThat(holder.reasoningEffort).isEqualTo(ReasoningEffort.DEFAULT);
    }

    @Test
    void configuredSamplingIsKeptForTheRequests() {
        JitLLMModelHolder holder = new JitLLMModelHolder(Optional.empty(), "ggml-org/Qwen3-0.6B-GGUF", "f16",
                0.6, 0.9, 42, 1024, true, false, 1, false, null, "4GB");

        assertThat(holder.temperature).isEqualTo(0.6);
        assertThat(holder.topP).isEqualTo(0.9);
        assertThat(holder.seed).isEqualTo(42);
        assertThat(holder.maxTokens).isEqualTo(1024);
    }

    @Test
    void unconfiguredSamplingFallsBackToTheFamilyDefaults() {
        JitLLMModelHolder qwen = unconfigured("ggml-org/Qwen3-0.6B-GGUF");
        JitLLMModelHolder llama = unconfigured(Consts.DEFAULT_CHAT_MODEL_NAME);

        assertThat(qwen.temperature).isEqualTo(0.8);
        assertThat(qwen.topP).isEqualTo(0.9);
        assertThat(llama.temperature).isEqualTo(0.3);
        assertThat(llama.topP).isEqualTo(0.95);
    }

    private static JitLLMModelHolder withReasoning(boolean enableThinking, ReasoningEffort effort) {
        return new JitLLMModelHolder(Optional.empty(), "unsloth/Qwen3.8-27B-GGUF", "Q4_0",
                null, null, null, null, null, null, null, enableThinking, effort, null);
    }

    @Test
    void configuredReasoningEffortReachesAModelThatControlsIt() {
        ModelCapabilities controllable = new ModelCapabilities(true, true, true);

        assertThat(withReasoning(true, ReasoningEffort.LOW).effectiveReasoningEffort(controllable))
                .isEqualTo(ReasoningEffort.LOW);
        assertThat(withReasoning(true, null).effectiveReasoningEffort(controllable))
                .isEqualTo(ReasoningEffort.DEFAULT);
    }

    @Test
    void reasoningEffortIsDroppedWhereTheEngineWouldRejectIt() {
        ModelCapabilities noEffortControl = new ModelCapabilities(true, true, false);
        ModelCapabilities controllable = new ModelCapabilities(true, true, true);

        // A family without the control, and thinking turned off: the engine refuses an explicit
        // effort for both, so the property is a no-op instead of a startup failure.
        assertThat(withReasoning(true, ReasoningEffort.MEDIUM).effectiveReasoningEffort(noEffortControl))
                .isEqualTo(ReasoningEffort.DEFAULT);
        assertThat(withReasoning(false, ReasoningEffort.MEDIUM).effectiveReasoningEffort(controllable))
                .isEqualTo(ReasoningEffort.DEFAULT);
    }
}
