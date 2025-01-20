package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface EmbeddingModelConfig {

    /**
     * The id of the model to be used.
     * <p>
     * All available models are listed in the IBM Watsonx.ai documentation at the <a href="
     * https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models-embed.html?context=wx&audience=wdp#ibm-provided">following
     * link</a>.
     * <p>
     * To use a model, locate the <code>API model_id</code> column in the table and copy the corresponding model ID.
     */
    @WithDefault("ibm/slate-125m-english-rtrvr")
    String modelId();

    /**
     * Represents the maximum number of input tokens accepted. This can be used to avoid requests failing due to input being
     * longer
     * than configured limits. If the text is truncated, then it truncates the end of the input (on the right), so the start of
     * the
     * input will remain the same. If this value exceeds the maximum sequence length (refer to the documentation to find this
     * value
     * for the model) then the call will fail if the total number of tokens exceeds the maximum sequence length.
     */
    Optional<Integer> truncateInputTokens();

    /**
     * Whether embedding model requests should be logged.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether embedding model responses should be logged.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();
}
